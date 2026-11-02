use std::sync::OnceLock;
use jni::{JNIEnv, objects::{JClass, JString}, sys::jstring};
use moviebox_tui::{service::MovieBoxService, providers::{ProviderKind, ReleaseProvider, Release, PlaybackSource, ResolutionIntent}};
use serde_json::{Value, json};

static RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();
static SERVICE: OnceLock<MovieBoxService> = OnceLock::new();
fn field<'a>(p: &'a Value, key: &str) -> Result<&'a str, String> {
    p[key].as_str().filter(|s| !s.is_empty()).ok_or_else(|| format!("Missing {key}"))
}
fn number(p: &Value, key: &str, default: usize) -> usize { p[key].as_u64().unwrap_or(default as u64) as usize }
fn http(url: &str) -> bool { url::Url::parse(url).is_ok_and(|u| matches!(u.scheme(), "https" | "http") && u.host_str().is_some()) }
fn provider(p: &Value) -> Result<ProviderKind, String> {
    ProviderKind::parse(field(p, "provider")?).filter(|p| ProviderKind::ENABLED.contains(p)).ok_or("Unsupported provider".into())
}
async fn request(action: &str, p: &Value) -> Result<Value, String> {
    let service = SERVICE.get_or_init(MovieBoxService::new);
    if action == "resolve" {
        let release: Release = serde_json::from_value(p["release"].clone()).map_err(|e| e.to_string())?;
        if !ProviderKind::ENABLED.contains(&release.provider) { return Err("Unsupported provider".into()); }
        let mut source = if release.provider == ProviderKind::FourKHdHub {
            service.fourk_client.as_ref().ok_or("4KHDHub unavailable")?.resolve_release(&release, ResolutionIntent::Playback).await.map_err(|e| e.to_string())?
        } else {
            let mirror = release.mirrors.first().ok_or("No playable stream mirrors")?;
            PlaybackSource { provider: release.provider, url: mirror.resolver_url.clone(), headers: mirror.headers.clone(), subtitle: None, source_label: mirror.label.clone(), max_height: Some(release.resolution_u64()) }
        };
        if !http(&source.url) { return Err("Only HTTP/HTTPS streams are supported".into()); }
        if let Some(subtitle) = p["subtitle"].as_str().filter(|s| !s.is_empty()) {
            if !http(subtitle) { return Err("Invalid subtitle URL".into()); }
            source.subtitle = Some(subtitle.into());
        }
        return Ok(json!(source));
    }
    let kind = provider(p)?;
    match action {
        "home" => { if kind != ProviderKind::MovieBox { return Err("Discovery uses MovieBox; use search for other providers".into()); }
            let (items, _) = service.homepage("2", number(p, "page", 1).max(1)).await?; Ok(json!(items)) }
        "search" => Ok(json!(service.search_typed(kind, field(p,"query")?, number(p,"page",1).max(1)).await.map_err(|e|e.user_message(kind))?)),
        "details" => Ok(json!(service.details_typed(kind, field(p,"id")?).await.map_err(|e|e.user_message(kind))?)),
        "releases" => {
            let id=field(p,"id")?; let season=number(p,"season",0); let episode=number(p,"episode",0);
            let result=match kind {
                ProviderKind::MovieBox => ReleaseProvider::episode_streams(&service.client,id,season,episode).await,
                ProviderKind::FourKHdHub => ReleaseProvider::episode_streams(service.fourk_client.as_ref().ok_or("4KHDHub unavailable")?,id,season,episode).await,
                ProviderKind::Dramachi => ReleaseProvider::episode_streams(&service.dramachi_client,id,season,episode).await,
                ProviderKind::BdixCircleFtp => ReleaseProvider::episode_streams(&service.circleftp_client,id,season,episode).await,
                ProviderKind::BdixDhakaFlix => ReleaseProvider::episode_streams(&service.dhakaflix_client,id,season,episode).await,
                ProviderKind::Addons => return Err("Addons are not included in this TV preview".into()),
            }; Ok(json!(result.map_err(|e|e.user_message(kind))?))
        }
        "subtitles" => { if kind!=ProviderKind::MovieBox {return Ok(json!([]));}
            Ok(json!(service.get_ext_captions(field(p,"id")?,field(p,"resource_id")?,&[],number(p,"season",0),number(p,"episode",0)).await?)) }
        _ => Err("Unknown TV backend request".into()),
    }
}
pub fn dispatch(input: &str) -> String {
    let result=std::panic::catch_unwind(|| -> Result<Value,String> {
        if input.len()>1024*1024 {return Err("Request too large".into());}
        let p:Value=serde_json::from_str(input).map_err(|_|"Invalid JSON request")?;
        let rt=RUNTIME.get_or_init(|| {let _=rustls::crypto::ring::default_provider().install_default();tokio::runtime::Builder::new_multi_thread().worker_threads(3).enable_all().build().expect("TV runtime")});
        rt.block_on(async {tokio::time::timeout(std::time::Duration::from_secs(100),request(p["action"].as_str().unwrap_or(""),&p["payload"])).await.map_err(|_|"Provider timed out. Try another source.".to_string())?})
    });
    match result {Ok(Ok(data))=>json!({"ok":true,"data":data}).to_string(),Ok(Err(error))=>json!({"ok":false,"error":error}).to_string(),Err(_)=>json!({"ok":false,"error":"Backend error. Try another title or provider."}).to_string()}
}
fn java_string(env: &mut JNIEnv, text: String) -> jstring {env.new_string(text).map(|s|s.into_raw()).unwrap_or(std::ptr::null_mut())}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_moviebox_tv_NativeBridge_nativeRequest(mut env:JNIEnv,_class:JClass,input:JString)->jstring {
    let text=env.get_string(&input).map(|s|s.to_string_lossy().into_owned());
    let output=match text {Ok(text)=>dispatch(&text),Err(_)=>json!({"ok":false,"error":"Invalid native request"}).to_string()};
    java_string(&mut env,output)
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_moviebox_tv_NativeBridge_nativeInit(mut env:JNIEnv,_class:JClass,root:JString)->jstring {
    let root=env.get_string(&root).map(|s|s.to_string_lossy().into_owned());
    let result=match root {
        Ok(root)=>{
            #[cfg(target_os="android")]
            let _=moviebox_tui::config::set_android_app_root(std::path::PathBuf::from(&root));
            let _=root;
            json!({"ok":true})
        },
        Err(_)=>json!({"ok":false,"error":"Cannot initialize private app storage"}),
    };
    java_string(&mut env,result.to_string())
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn rejects_invalid_input(){assert!(!serde_json::from_str::<Value>(&dispatch("not-json")).unwrap()["ok"].as_bool().unwrap());}
    #[test] fn rejects_unknown_provider(){assert!(!serde_json::from_str::<Value>(&dispatch(r#"{"action":"search","payload":{"provider":"bad","query":"test"}}"#)).unwrap()["ok"].as_bool().unwrap());}
    #[test] fn validates_stream_schemes(){assert!(http("https://example.com/a.m3u8"));assert!(!http("file:///data/private"));assert!(!http("javascript:alert(1)"));}
}
