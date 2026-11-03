//! Private JSON bridge for the desktop UI. One request on stdin, one response on stdout.
use moviebox_tui::{
    player,
    providers::{PlaybackSource, ProviderKind, Release, ReleaseProvider, ResolutionIntent},
    service::MovieBoxService,
};
use serde_json::{Value, json};
use std::io::{self, Read};
use std::process::Stdio;

fn text<'a>(v: &'a Value, key: &str) -> Result<&'a str, String> {
    v.get(key)
        .and_then(Value::as_str)
        .filter(|s| !s.is_empty())
        .ok_or_else(|| format!("Missing {key}"))
}
fn number(v: &Value, key: &str, default: usize) -> usize {
    v.get(key).and_then(Value::as_u64).unwrap_or(default as u64) as usize
}
fn http_url(value: &str) -> bool {
    url::Url::parse(value)
        .is_ok_and(|u| matches!(u.scheme(), "http" | "https") && u.host_str().is_some())
}
fn audio_argument(kind: player::PlayerKind, code: &str) -> Result<Option<String>, String> {
    let languages = match code {
        "" => return Ok(None),
        "hin" => "hin,hi,hindi",
        "eng" => "eng,en,english",
        "tam" => "tam,ta,tamil",
        "tel" => "tel,te,telugu",
        "ben" => "ben,bn,bengali",
        "mal" => "mal,ml,malayalam",
        "kan" => "kan,kn,kannada",
        "mar" => "mar,mr,marathi",
        "pan" => "pan,pa,punjabi",
        "urd" => "urd,ur,urdu",
        _ => return Err("Unsupported audio language".into()),
    };
    let flag = match kind {
        player::PlayerKind::Mpv => "--alang",
        player::PlayerKind::Vlc => "--audio-language",
        player::PlayerKind::Iina => "--mpv-alang",
        player::PlayerKind::AndroidIntent => return Ok(None),
    };
    Ok(Some(format!("{flag}={languages}")))
}

// This binary is private to the desktop controller. These options are generated
// by Python, not passed through from WebView requests. Existing TUI is unchanged.
fn add_tracking(
    cmd: &mut std::process::Command,
    kind: player::PlayerKind,
    t: &Value,
) -> Result<bool, String> {
    match kind {
        player::PlayerKind::Mpv => {
            let script = text(t, "mpv_script")?;
            let path = std::path::Path::new(script);
            if !path.is_absolute()
                || path.extension().and_then(|s| s.to_str()) != Some("lua")
                || !path.is_file()
            {
                return Err("Invalid playback tracker".into());
            }
            cmd.arg(format!("--script={script}"));
            Ok(true)
        }
        player::PlayerKind::Vlc => {
            let port = t
                .get("vlc_port")
                .and_then(Value::as_u64)
                .filter(|p| (1024..=65535).contains(p))
                .ok_or("Invalid tracker port")?;
            let secret = text(t, "vlc_password")?;
            if !(16..=128).contains(&secret.len())
                || !secret
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b"-_".contains(&b))
            {
                return Err("Invalid tracker credentials".into());
            }
            cmd.arg("--extraintf=http")
                .arg("--http-host=127.0.0.1")
                .arg(format!("--http-port={port}"))
                .arg(format!("--http-password={secret}"));
            Ok(true)
        }
        _ => Ok(false),
    }
}

async fn dispatch(action: &str, p: &Value) -> Result<Value, String> {
    if action == "players" {
        return Ok(json!(
            player::detect()
                .iter()
                .map(|p| json!({"id": p.config_key(), "label": p.label()}))
                .collect::<Vec<_>>()
        ));
    }
    let service = MovieBoxService::new();
    if action == "play" {
        let release: Release =
            serde_json::from_value(p["release"].clone()).map_err(|e| e.to_string())?;
        let kind = player::PlayerKind::parse(text(p, "player")?).ok_or("Unknown player")?;
        let audio_code = p
            .get("audio_language")
            .and_then(Value::as_str)
            .unwrap_or("");
        let audio_arg = audio_argument(kind, audio_code)?;
        if !player::detect().contains(&kind) {
            return Err("Player not found. Install VLC or mpv, then restart the app.".into());
        }
        let mut source = if release.provider == ProviderKind::FourKHdHub {
            service
                .fourk_client
                .as_ref()
                .ok_or("4KHDHub unavailable")?
                .resolve_release(&release, ResolutionIntent::Playback)
                .await
                .map_err(|e| e.to_string())?
        } else {
            let mirror = release
                .mirrors
                .first()
                .ok_or("No stream mirrors available")?;
            PlaybackSource {
                provider: release.provider,
                url: mirror.resolver_url.clone(),
                headers: mirror.headers.clone(),
                subtitle: None,
                source_label: mirror.label.clone(),
                max_height: Some(release.resolution_u64()),
            }
        };
        if !http_url(&source.url) {
            return Err("Only HTTP/HTTPS streams are supported".into());
        }
        let mut subtitle = p
            .get("subtitle")
            .and_then(Value::as_str)
            .filter(|s| !s.is_empty())
            .map(str::to_owned);
        if let Some(sub) = subtitle.as_deref() {
            if !http_url(sub) {
                return Err("Invalid subtitle URL".into());
            }
            let path = service
                .download_subtitle_file(sub, &source.headers, None)
                .await?;
            subtitle = Some(path.to_string_lossy().into_owned());
        }
        let source_host = url::Url::parse(&source.url)
            .ok()
            .and_then(|u| u.host_str().map(str::to_owned))
            .unwrap_or_default();
        let needs_proxy = player::is_dash_url(&source.url)
            || (kind == player::PlayerKind::Vlc
                && source.headers.iter().any(|(k, _)| {
                    !k.eq_ignore_ascii_case("referer") && !k.eq_ignore_ascii_case("user-agent")
                }));
        let mut proxy = None;
        if needs_proxy {
            let (local_url, child) = moviebox_tui::proxy::spawn_sidecar(
                &source.url,
                &source.headers,
                None,
                source.max_height,
            )?;
            source.url = local_url;
            source.headers.clear();
            proxy = Some(child);
        }
        let resume = p.get("resume_seconds").and_then(Value::as_u64).unwrap_or(0);
        if resume > 604800 {
            return Err("Invalid resume position".into());
        }
        let mut cmd = player::command(
            kind,
            &source.url,
            subtitle.as_deref(),
            &source.headers,
            None,
            Some(resume),
            None,
            source.max_height,
        );
        let tracked = add_tracking(&mut cmd, kind, &p["_tracking"])?;
        if let Some(arg) = audio_arg {
            cmd.arg(arg);
        }
        cmd.stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null());
        player::configure_detached_process(&mut cmd);
        match cmd.spawn() {
            Ok(_) => Ok(
                json!({"tracking": tracked, "source_host": source_host, "_media_url": source.url, "message": if audio_code.is_empty() {
                    format!("Opened in {}", kind.label())
                } else {
                    format!("Opened in {} with the selected audio preference. If the stream lacks that embedded track, the player may use its default; check its Audio menu.", kind.label())
                }}),
            ),
            Err(e) => {
                if let Some(mut child) = proxy {
                    let _ = child.kill();
                    let _ = child.wait();
                }
                Err(format!("Cannot open player: {e}"))
            }
        }
    } else {
        let provider = ProviderKind::parse(text(p, "provider")?)
            .filter(|p| ProviderKind::ENABLED.contains(p))
            .ok_or("Unsupported provider")?;
        match action {
            "home" => {
                if provider != ProviderKind::MovieBox {
                    return Err(
                        "Discovery feed uses MovieBox. Search is available for other providers."
                            .into(),
                    );
                }
                let (items, _) = service.homepage("2", number(p, "page", 1).max(1)).await?;
                Ok(json!(items))
            }
            "search" => {
                let query = text(p, "query")?;
                let items = service
                    .search_typed(provider, query, number(p, "page", 1).max(1))
                    .await
                    .map_err(|e| e.user_message(provider))?;
                Ok(json!(items))
            }
            "details" => Ok(json!(
                service
                    .details_typed(provider, text(p, "id")?)
                    .await
                    .map_err(|e| e.user_message(provider))?
            )),
            "releases" => {
                let id = text(p, "id")?;
                let season = number(p, "season", 0);
                let episode = number(p, "episode", 0);
                let result = match provider {
                    ProviderKind::MovieBox => {
                        service.client.episode_streams(id, season, episode).await
                    }
                    ProviderKind::FourKHdHub => {
                        service
                            .fourk_client
                            .as_ref()
                            .ok_or("4KHDHub unavailable")?
                            .episode_streams(id, season, episode)
                            .await
                    }
                    ProviderKind::Dramachi => {
                        ReleaseProvider::episode_streams(
                            &service.dramachi_client,
                            id,
                            season,
                            episode,
                        )
                        .await
                    }
                    ProviderKind::BdixCircleFtp => {
                        service
                            .circleftp_client
                            .episode_streams(id, season, episode)
                            .await
                    }
                    ProviderKind::BdixDhakaFlix => {
                        service
                            .dhakaflix_client
                            .episode_streams(id, season, episode)
                            .await
                    }
                    ProviderKind::Addons => {
                        return Err("Addons are not supported in this desktop version".into());
                    }
                };
                Ok(json!(result.map_err(|e| e.user_message(provider))?))
            }
            "subtitles" => {
                if provider != ProviderKind::MovieBox {
                    return Ok(json!([]));
                }
                Ok(json!(
                    service
                        .get_ext_captions(
                            text(p, "id")?,
                            text(p, "resource_id")?,
                            &[],
                            number(p, "season", 0),
                            number(p, "episode", 0)
                        )
                        .await?
                ))
            }
            _ => Err("Unknown desktop request".into()),
        }
    }
}
#[tokio::main]
async fn main() {
    // The upstream VLC/DASH proxy relaunches the current binary in sidecar mode.
    let args: Vec<String> = std::env::args().collect();
    if args.get(1).is_some_and(|s| s == "--proxy-for-vlc") {
        let headers = args
            .get(3)
            .and_then(|s| serde_json::from_str(s).ok())
            .unwrap_or_default();
        moviebox_tui::proxy::run_sidecar(
            args.get(2).cloned().unwrap_or_default(),
            headers,
            args.get(4).cloned().filter(|s| !s.is_empty()),
            args.get(5).and_then(|s| s.parse().ok()),
        )
        .await;
        return;
    }
    let _ = rustls::crypto::ring::default_provider().install_default();
    let mut input = String::new();
    let result = match io::stdin().take(2 * 1024 * 1024).read_to_string(&mut input) {
        Ok(_) => match serde_json::from_str::<Value>(&input) {
            Ok(request) => match tokio::time::timeout(
                std::time::Duration::from_secs(100),
                dispatch(
                    request["action"].as_str().unwrap_or(""),
                    &request["payload"],
                ),
            )
            .await
            {
                Ok(result) => result,
                Err(_) => Err("Provider timed out. Try again or select another provider.".into()),
            },
            Err(_) => Err("Invalid JSON request".into()),
        },
        Err(e) => Err(e.to_string()),
    };
    let output = match result {
        Ok(data) => json!({"ok": true, "data": data}),
        Err(error) => json!({"ok": false, "error": error}),
    };
    println!("{output}");
}

#[cfg(test)]
mod desktop_audio_tests {
    use super::*;
    #[test]
    fn hindi_mpv_preference() {
        assert_eq!(
            audio_argument(player::PlayerKind::Mpv, "hin")
                .unwrap()
                .unwrap(),
            "--alang=hin,hi,hindi"
        );
    }
    #[test]
    fn english_vlc_preference() {
        assert_eq!(
            audio_argument(player::PlayerKind::Vlc, "eng")
                .unwrap()
                .unwrap(),
            "--audio-language=eng,en,english"
        );
    }
    #[test]
    fn default_does_not_override_stream() {
        assert!(
            audio_argument(player::PlayerKind::Mpv, "")
                .unwrap()
                .is_none()
        );
    }
    #[test]
    fn unknown_language_rejected() {
        assert!(audio_argument(player::PlayerKind::Vlc, "--arbitrary-option").is_err());
    }
}
