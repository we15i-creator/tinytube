// Herax Browser Core — Rust .so
// Tor-routed | Ad-protected | Anonymous

use std::ffi::{CStr, CString};
use std::os::raw::c_char;

fn to_rust(p: *const c_char) -> String {
    if p.is_null() { return String::new(); }
    unsafe { CStr::from_ptr(p) }.to_str().unwrap_or("").to_string()
}

fn to_c(s: &str) -> *mut c_char {
    CString::new(s).unwrap_or_default().into_raw()
}

#[no_mangle] pub extern "C" fn herax_free(p: *mut c_char) {
    if !p.is_null() { unsafe { let _ = CString::from_raw(p); } }
}

// ─── Tor ───────────────────────────────────────
#[no_mangle] pub extern "C" fn herax_tor_proxy() -> *mut c_char {
    to_c("socks5://127.0.0.1:9050")
}

#[no_mangle] pub extern "C" fn herax_use_tor(url: *const c_char) -> *mut c_char {
    let url = to_rust(url);
    to_c(if url.contains(".onion") || url.contains("youtube.com") { "tor" } else { "direct" })
}

// ─── Ad Block ──────────────────────────────────
const TRACKERS: &[&str] = &[
    "doubleclick.net","googleadservices.com","googlesyndication.com",
    "google-analytics.com","googletagmanager.com","facebook.com/tr",
    "amazon-adsystem.com","adnxs.com","adsrvr.org","criteo.com",
    "outbrain.com","taboola.com","pubmatic.com","openx.net"
];

#[no_mangle] pub extern "C" fn herax_block(url: *const c_char) -> *mut c_char {
    let url = to_rust(url).to_lowercase();
    to_c(if TRACKERS.iter().any(|t| url.contains(t)) { "blocked" } else { "allow" })
}

// ─── Privacy ───────────────────────────────────
#[no_mangle] pub extern "C" fn herax_user_agent() -> *mut c_char {
    to_c("Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0")
}

#[no_mangle] pub extern "C" fn herax_timezone() -> *mut c_char { to_c("UTC") }
#[no_mangle] pub extern "C" fn herax_language() -> *mut c_char { to_c("en-US,en;q=0.9") }

// ─── YouTube Cleaner ───────────────────────────
#[no_mangle] pub extern "C" fn herax_youtube_cleaner() -> *mut c_char {
    to_c(r#"
(function(){const k=()=>{['ytd-display-ad-renderer','.ytp-ad-module','ytd-promoted-video-renderer']
.forEach(s=>document.querySelectorAll(s).forEach(e=>e.remove()));
const skip=document.querySelector('.ytp-ad-skip-button');if(skip)skip.click();};
k();setInterval(k,300);new MutationObserver(k).observe(document.body,{childList:true,subtree:true});
document.cookie='CONSENT=YES+; domain=.youtube.com; path=/';})();
"#)
}

// ─── Version ───────────────────────────────────
#[no_mangle] pub extern "C" fn herax_version() -> *mut c_char {
    to_c("Herax Browser v0.1.0 — Tor-routed | Ad-protected | Anonymous")
  }
