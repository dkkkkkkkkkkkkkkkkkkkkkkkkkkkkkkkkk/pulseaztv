# -*- coding: utf-8 -*-
# ╔══════════════════════════════════════════════════════════╗
# ║  PulseAZ TV — AZ TV SİSTEMİ v4.0                         ║
# ║  Real HLS · FFmpeg · Efir Proqramı · Xəbərlər · Admin    ║
# ╚══════════════════════════════════════════════════════════╝
import subprocess, threading, time, json, os, signal
import requests
from flask import Flask, render_template_string, request, redirect, url_for, session, jsonify, send_from_directory
from functools import wraps
from datetime import datetime, timedelta
from werkzeug.security import generate_password_hash, check_password_hash

app = Flask(__name__)
app.secret_key = os.environ.get('SECRET_KEY', 'pulseaz_tv_secret_2026')

CHANNEL_NAME = 'PulseAZ TV'
CHANNEL_TAGLINE = 'AZƏRBAYCAN · 24/7 CANLI'
CHANNEL_LOGO = 'https://i.postimg.cc/KcTwCmmx/file-00000000aa2081f48a42e74b7ca90e05-removebg-preview.png'

ADMIN_USER = 'admin'
ADMIN_PASS_HASH = generate_password_hash('ekber20132014')

DATA_FILE = '/tmp/pulseaz_data.json'
LIVE_DIR = '/tmp/live'
LIVE_PLAYLIST = os.path.join(LIVE_DIR, 'index.m3u8')
LOGO_FILE = os.path.join(LIVE_DIR, 'logo.png')
os.makedirs(LIVE_DIR, exist_ok=True)
START_TIME = time.time()

# ==================== DATA (növbə + proqram + xəbərlər bir faylda) ====================
_lock = threading.Lock()

def load_data():
    if os.path.exists(DATA_FILE):
        try:
            with open(DATA_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception: pass
    return {'videos': [], 'schedule': [], 'news': []}

def save_data(d):
    with open(DATA_FILE, 'w', encoding='utf-8') as f:
        json.dump(d, f, indent=2, ensure_ascii=False)

def login_required(f):
    @wraps(f)
    def d(*args, **kwargs):
        if not session.get('logged_in'):
            return redirect(url_for('login'))
        return f(*args, **kwargs)
    return d

# ==================== LOGO ====================
def ensure_logo():
    if os.path.exists(LOGO_FILE): return True
    try:
        r = requests.get(CHANNEL_LOGO, timeout=15); r.raise_for_status()
        with open(LOGO_FILE, 'wb') as f: f.write(r.content)
        return True
    except Exception as e:
        print('[LOGO]', e); return False

# ==================== BROADCAST ENGINE ====================
ffmpeg_proc = None
viewers = 0

def stop_stream():
    global ffmpeg_proc
    if ffmpeg_proc and ffmpeg_proc.poll() is None:
        try:
            ffmpeg_proc.send_signal(signal.SIGINT)
            ffmpeg_proc.wait(timeout=8)
        except Exception:
            try: ffmpeg_proc.kill()
            except Exception: pass
    ffmpeg_proc = None
    for f in os.listdir(LIVE_DIR):
        if f == 'logo.png': continue
        try: os.remove(os.path.join(LIVE_DIR, f))
        except Exception: pass

def start_stream(video):
    global ffmpeg_proc
    stop_stream()
    if ensure_logo():
        fc = "[0:v]scale=-2:720[base];[1:v]scale=170:-1[wm];[base][wm]overlay=28:28"
        inputs = ['-re', '-i', video['url'], '-i', LOGO_FILE, '-filter_complex', fc]
    else:
        inputs = ['-re', '-i', video['url'], '-vf', 'scale=-2:720']
    cmd = ['ffmpeg', '-hide_banner', '-loglevel', 'error', *inputs,
        '-c:v', 'libx264', '-preset', 'veryfast', '-tune', 'zerolatency',
        '-b:v', '2500k', '-maxrate', '2500k', '-bufsize', '5000k',
        '-c:a', 'aac', '-b:a', '128k', '-ar', '44100',
        '-f', 'hls', '-hls_time', '4', '-hls_list_size', '6',
        '-hls_flags', 'delete_segments+append_list+omit_endlist',
        '-hls_segment_filename', os.path.join(LIVE_DIR, 'seg_%05d.ts'), LIVE_PLAYLIST]
    print(f'[EFİR] {video["name"]}')
    ffmpeg_proc = subprocess.Popen(cmd)

def broadcaster():
    global ffmpeg_proc
    while True:
        vids = [v for v in load_data()['videos'] if v.get('active', True)]
        if not vids:
            stop_stream(); time.sleep(5); continue
        current = vids[0]
        start_stream(current)
        while True:
            time.sleep(2)
            if ffmpeg_proc is None or ffmpeg_proc.poll() is not None: break
            if not any(v['id'] == current['id'] for v in load_data()['videos']): break
        with _lock:
            d = load_data()
            d['videos'] = [v for v in d['videos'] if v['id'] != current['id']]
            save_data(d)

threading.Thread(target=broadcaster, daemon=True).start()

# ==================== STREAM ====================
@app.route('/live.m3u8')
def live_m3u8():
    return send_from_directory(LIVE_DIR, 'index.m3u8',
        mimetype='application/vnd.apple.mpegurl',
        headers={'Cache-Control': 'no-store, no-cache'})

@app.route('/live/<path:fname>')
def live_seg(fname):
    return send_from_directory(LIVE_DIR, fname,
        mimetype='video/mp2t',
        headers={'Cache-Control': 'no-store, no-cache'})

@app.route('/api/live-status')
def live_status():
    d = load_data()
    vids = [v for v in d['videos'] if v.get('active', True)]
    on_air = ffmpeg_proc is not None and ffmpeg_proc.poll() is None
    return jsonify(live=on_air and bool(vids),
                   now=vids[0]['name'] if vids else None,
                   queue_count=len(vids), viewers=viewers,
                   uptime=int(time.time() - START_TIME))

# ==================== SƏHİFƏLƏR ====================
@app.route('/')
def index():
    d = load_data()
    vids = [v for v in d['videos'] if v.get('active', True)]
    on_air = ffmpeg_proc is not None and ffmpeg_proc.poll() is not None
    return render_template_string(INDEX_HTML, videos=d['videos'], live_now=vids[0] if vids else None,
                                  is_live=on_air and bool(vids), schedule=d.get('schedule', []),
                                  news=d.get('news', []))

@app.route('/login', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        if request.form.get('username') == ADMIN_USER and \
           check_password_hash(ADMIN_PASS_HASH, request.form.get('password', '')):
            session['logged_in'] = True
            return redirect(url_for('admin'))
        return render_template_string(LOGIN_HTML, error='❌ İstifadəçi adı və ya şifrə yanlışdır!')
    return render_template_string(LOGIN_HTML, error=None)

@app.route('/logout')
def logout():
    session.pop('logged_in', None)
    return redirect(url_for('index'))

@app.route('/admin', methods=['GET', 'POST'])
@login_required
def admin():
    message, msg_type = None, 'success'
    if request.method == 'POST':
        action = request.form.get('action')
        with _lock:
            d = load_data()
            if action == 'add':
                name = request.form.get('name', '').strip()
                url = request.form.get('url', '').strip()
                if name and url.startswith(('http',)):
                    nid = max([v['id'] for v in d['videos']], default=0) + 1
                    d['videos'].append({'id': nid, 'name': name, 'url': url, 'active': True,
                                        'added': datetime.now().strftime('%H:%M')})
                    message = f'✅ "{name}" yayıma əlavə olundu!'
                else: message, msg_type = '⚠️ Ad və keçərli URL daxil et!', 'warning'
            elif action == 'del_video':
                vid = int(request.form.get('video_id'))
                d['videos'] = [v for v in d['videos'] if v['id'] != vid]
                message = '🗑️ Silindi!'
            elif action == 'up_video':
                vid = int(request.form.get('video_id'))
                for i, v in enumerate(d['videos']):
                    if v['id'] == vid and i > 0:
                        d['videos'][i-1], d['videos'][i] = d['videos'][i], d['videos'][i-1]
                        break
                message = '🔄 Sıra dəyişdi!'
            elif action == 'add_sched':
                t = request.form.get('time', '').strip()
                n = request.form.get('name', '').strip()
                if t and n:
                    nid = max([s['id'] for s in d.get('schedule', [])], default=0) + 1
                    d.setdefault('schedule', []).append({'id': nid, 'time': t, 'name': n})
                    d['schedule'].sort(key=lambda s: s['time'])
                    message = f'✅ Proqrama "{n}" əlavə olundu!'
                else: message, msg_type = '⚠️ Saat və ad daxil et!', 'warning'
            elif action == 'del_sched':
                sid = int(request.form.get('sched_id'))
                d['schedule'] = [s for s in d.get('schedule', []) if s['id'] != sid]
                message = '🗑️ Proqramdan silindi!'
            elif action == 'add_news':
                t = request.form.get('title', '').strip()
                if t:
                    nid = max([n['id'] for n in d.get('news', [])], default=0) + 1
                    d.setdefault('news', []).insert(0, {'id': nid, 'title': t,
                        'date': datetime.now().strftime('%d.%m.%Y %H:%M')})
                    message = '📰 Xəbər əlavə olundu!'
                else: message, msg_type = '⚠️ Xəbər mətni boşdur!', 'warning'
            elif action == 'del_news':
                nid = int(request.form.get('news_id'))
                d['news'] = [n for n in d.get('news', []) if n['id'] != nid]
                message = '🗑️ Xəbər silindi!'
            save_data(d)
    d = load_data()
    return render_template_string(ADMIN_HTML, videos=d['videos'], schedule=d.get('schedule', []),
                                  news=d.get('news', []), message=message, msg_type=msg_type)

# ==================== CSS ====================
BASE_CSS = '''
@import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@500;700;900&family=Inter:wght@400;600;700;800&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
:root{--gold:#ffd700;--gold2:#ff8c00;--bg:#06060e;--purple:#7b2ff7;--live:#ff0044}
html{scroll-behavior:smooth}
body{font-family:'Inter',sans-serif;background:var(--bg);color:#fff;min-height:100vh;overflow-x:hidden}
.bg{position:fixed;inset:0;z-index:-1;background:
 radial-gradient(ellipse 70% 50% at 15% 5%,rgba(123,47,247,.10),transparent 55%),
 radial-gradient(ellipse 60% 45% at 90% 15%,rgba(255,215,0,.07),transparent 55%),
 linear-gradient(180deg,#06060e,#0c0c1e)}
.bg::before{content:'';position:absolute;inset:0;
 background-image:radial-gradient(rgba(255,215,0,.06) 1px,transparent 1px);
 background-size:40px 40px;animation:drift 70s linear infinite;opacity:.5}
@keyframes drift{to{background-position:40px 800px}}
.navbar{position:sticky;top:0;z-index:50;display:flex;justify-content:space-between;align-items:center;
 flex-wrap:wrap;gap:12px;padding:12px 28px;background:rgba(8,8,20,.72);backdrop-filter:blur(24px);
 border-bottom:1px solid rgba(255,215,0,.18);box-shadow:0 12px 40px rgba(0,0,0,.55)}
.logo{font-family:'Orbitron';font-size:23px;font-weight:900;display:flex;align-items:center;gap:12px;
 animation:glowPulse 3s ease-in-out infinite}
@keyframes glowPulse{0%,100%{filter:drop-shadow(0 0 5px rgba(255,215,0,.25))}50%{filter:drop-shadow(0 0 18px rgba(255,215,0,.55))}}
.logo img{width:40px;height:40px;object-fit:contain}
.logo .brand{background:linear-gradient(135deg,var(--gold),var(--gold2));-webkit-background-clip:text;
 background-clip:text;-webkit-text-fill-color:transparent}
.logo .az{font-size:10px;letter-spacing:3px;color:rgba(255,255,255,.35);border-left:1px solid rgba(255,255,255,.12);padding-left:10px}
.nav-links{display:flex;gap:6px;flex-wrap:wrap}
.nav-links a{color:rgba(255,255,255,.6);text-decoration:none;padding:8px 16px;border-radius:10px;
 transition:.3s;font-weight:600;font-size:13px}
.nav-links a:hover{background:rgba(255,215,0,.08);color:var(--gold);transform:translateY(-2px)}
.nav-links a.active{background:rgba(255,215,0,.12);color:var(--gold)}
.logout-btn{color:rgba(255,255,255,.4)!important;border:1px solid rgba(255,255,255,.08)}
.logout-btn:hover{border-color:#ff4444;color:#ff4444!important}
.container{max-width:1200px;margin:0 auto;padding:26px 20px}
.player-shell{position:relative;border-radius:22px;padding:2px;
 background:linear-gradient(135deg,rgba(255,215,0,.35),rgba(123,47,247,.3),rgba(255,215,0,.15));
 box-shadow:0 25px 80px rgba(0,0,0,.65);animation:introIn .9s cubic-bezier(.22,1,.36,1)}
@keyframes introIn{from{opacity:0;transform:translateY(30px) scale(.97)}to{opacity:1;transform:none}}
.player-wrapper{position:relative;padding-bottom:56.25%;height:0;background:#000;border-radius:20px;overflow:hidden}
.player-wrapper video{position:absolute;inset:0;width:100%;height:100%}
video::-webkit-media-timeline{display:none!important}
video::-webkit-media-controls-current-time-display{display:none!important}
video::-webkit-media-controls-time-remaining-display{display:none!important}
.overlay{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;flex-direction:column;
 gap:18px;background:rgba(4,4,12,.9);z-index:5;text-align:center;padding:20px;backdrop-filter:blur(4px)}
.overlay i{font-size:54px;color:rgba(255,215,0,.3)}
.overlay .msg{font-size:17px;color:rgba(255,255,255,.7)}
.spinner{width:52px;height:52px;border:4px solid rgba(255,215,0,.15);border-top-color:var(--gold);
 border-radius:50%;animation:spin .8s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}
.player-info{display:flex;justify-content:space-between;align-items:center;padding:16px 24px;margin-top:18px;
 background:rgba(255,255,255,.03);backdrop-filter:blur(16px);border-radius:16px;
 border:1px solid rgba(255,215,0,.1);flex-wrap:wrap;gap:12px;animation:fadeUp .7s .2s ease both}
.now-title{display:flex;align-items:center;gap:12px;font-size:19px;font-weight:700;min-width:0}
.now-title .name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;
 background:linear-gradient(90deg,#fff,#ffd900);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent}
.live-dot{width:12px;height:12px;background:var(--live);border-radius:50%;
 animation:pulse 1.5s ease-in-out infinite;flex-shrink:0;box-shadow:0 0 12px var(--live)}
@keyframes pulse{0%,100%{opacity:1;transform:scale(1)}50%{opacity:.3;transform:scale(.8)}}
.badge-live{font-size:11px;background:linear-gradient(135deg,var(--gold),var(--gold2));
 padding:3px 12px;border-radius:20px;font-weight:800;color:#000;flex-shrink:0}
.meta{display:flex;gap:14px;align-items:center;font-size:12px;color:rgba(255,255,255,.45)}
.meta b{color:var(--gold);font-family:'Orbitron';font-size:12px}
.quality{font-size:10px;font-weight:800;color:#00ff88;border:1px solid rgba(0,255,136,.3);padding:2px 8px;border-radius:6px}
.ctrl-btn{background:rgba(255,215,0,.08);border:1px solid rgba(255,215,0,.14);color:#fff;
 padding:9px 18px;border-radius:11px;cursor:pointer;transition:.3s;font-weight:600;font-size:13px;
 display:inline-flex;align-items:center;gap:8px;font-family:inherit}
.ctrl-btn:hover{background:rgba(255,215,0,.16);transform:translateY(-2px)}
.marquee{margin-top:16px;background:rgba(255,215,0,.04);border:1px solid rgba(255,215,0,.1);
 border-radius:12px;overflow:hidden;white-space:nowrap;position:relative;height:38px;display:flex;align-items:center}
.marquee::before{content:'SON DƏQİQƏ';position:absolute;left:0;z-index:2;
 background:linear-gradient(135deg,#ff0044,#cc0033);color:#fff;font-weight:800;font-size:11px;
 padding:0 14px;height:100%;display:flex;align-items:center;letter-spacing:1px}
.marquee span{display:inline-block;padding-left:100%;animation:scroll 28s linear infinite;
 font-size:13px;color:rgba(255,255,255,.65)}
@keyframes scroll{to{transform:translateX(-100%)}}
/* === 2 SÜTUNLU BÖLMƏ: PROQRAM + XƏBƏRLƏR (AZ TV üslubu) === */
.grid2{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-top:32px}
.panel{background:rgba(255,255,255,.025);backdrop-filter:blur(14px);border:1px solid rgba(255,215,0,.08);
 border-radius:18px;padding:22px;animation:fadeUp .7s .3s ease both}
.panel h2{font-size:17px;margin-bottom:16px;display:flex;align-items:center;gap:10px;color:var(--gold)}
.panel h2 span{background:rgba(255,215,0,.08);padding:2px 10px;border-radius:20px;font-size:12px;margin-left:auto}
.sched-item{display:flex;gap:14px;padding:10px 12px;border-radius:10px;transition:.3s;align-items:baseline}
.sched-item:hover{background:rgba(255,215,0,.05)}
.sched-item .t{font-family:'Orbitron';font-size:12px;color:var(--gold);min-width:52px}
.sched-item .n{font-size:14px;color:rgba(255,255,255,.8)}
.sched-item.now{background:rgba(255,215,0,.08);border-left:3px solid var(--gold)}
.sched-item.now .n{color:var(--gold);font-weight:700}
.news-item{padding:12px;border-radius:10px;transition:.3s;border-bottom:1px solid rgba(255,255,255,.04)}
.news-item:last-child{border-bottom:none}
.news-item:hover{background:rgba(255,215,0,.04)}
.news-item .d{font-size:11px;color:var(--gold);opacity:.7;margin-bottom:4px}
.news-item .t{font-size:14px;color:rgba(255,255,255,.85);line-height:1.4}
.section-title{font-size:18px;font-weight:800;margin:32px 0 15px;display:flex;align-items:center;gap:10px;color:var(--gold)}
.section-title span{background:rgba(255,215,0,.08);padding:2px 12px;border-radius:20px;font-size:13px}
.queue{display:flex;flex-direction:column;gap:8px}
.queue-item{display:flex;justify-content:space-between;align-items:center;gap:12px;
 background:rgba(255,255,255,.025);border:1px solid rgba(255,215,0,.07);border-radius:14px;
 padding:15px 20px;transition:.35s;flex-wrap:wrap;animation:fadeUp .5s ease both}
.queue-item:first-child{border-color:rgba(255,215,0,.4);background:rgba(255,215,0,.06);box-shadow:0 0 30px rgba(255,215,0,.06)}
.queue-item:hover{border-color:rgba(255,215,0,.22);transform:translateX(4px)}
.queue-item .qn{font-weight:600;font-size:14px;display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.queue-item .idx{color:var(--gold);font-family:'Orbitron';font-size:12px;opacity:.7}
.queue-item .qu{color:rgba(255,255,255,.28);font-size:11px;word-break:break-all;width:100%}
.empty-state{text-align:center;padding:40px 20px;color:rgba(255,255,255,.2)}
.empty-state i{font-size:40px;display:block;margin-bottom:12px;color:rgba(255,215,0,.1)}
.card{background:rgba(255,255,255,.025);backdrop-filter:blur(14px);border:1px solid rgba(255,215,0,.08);
 border-radius:18px;padding:26px;margin-bottom:25px}
.card h2{font-size:18px;margin-bottom:16px;display:flex;align-items:center;gap:10px;color:var(--gold);flex-wrap:wrap}
.form-row{display:flex;gap:12px;flex-wrap:wrap}
.form-row input{flex:1;min-width:130px;padding:12px 16px;background:rgba(255,255,255,.04);
 border:1px solid rgba(255,255,255,.08);border-radius:11px;color:#fff;font-size:14px;outline:none;transition:.3s;font-family:inherit}
.form-row input:focus{border-color:var(--gold);box-shadow:0 0 24px rgba(255,215,0,.08)}
.form-row input::placeholder{color:rgba(255,255,255,.25)}
.btn{padding:11px 22px;border:none;border-radius:11px;font-weight:700;cursor:pointer;transition:.3s;
 display:inline-flex;align-items:center;gap:8px;font-size:14px;text-decoration:none;font-family:inherit}
.btn:hover{transform:translateY(-2px)}
.btn-primary{background:linear-gradient(135deg,var(--gold),var(--gold2));color:#000;box-shadow:0 6px 24px rgba(255,180,0,.25)}
.btn-danger{background:linear-gradient(135deg,#ff0044,#cc0033);color:#fff}
.btn-outline{background:transparent;border:1px solid rgba(255,255,255,.12);color:#fff}
.btn-mini{padding:6px 12px;font-size:12px;border-radius:8px}
.message{padding:13px 18px;border-radius:12px;margin-bottom:20px;display:flex;align-items:center;gap:10px;animation:fadeUp .4s ease}
@keyframes fadeUp{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}
.message.success{background:rgba(0,255,136,.06);border:1px solid rgba(0,255,136,.14);color:#00ff88}
.message.warning{background:rgba(255,170,0,.07);border:1px solid rgba(255,170,0,.16);color:#ffaa00}
.stream-link{background:rgba(0,255,136,.04);border:1px dashed rgba(0,255,136,.3);border-radius:14px;
 padding:16px 20px;margin-bottom:25px;font-size:13px;color:rgba(255,255,255,.65)}
.stream-link code{color:#00ff88;background:rgba(0,0,0,.45);padding:4px 12px;border-radius:7px;
 word-break:break-all;display:inline-block;margin-top:8px}
.footer{text-align:center;padding:34px 20px 20px;color:rgba(255,255,255,.15);font-size:12px}
.footer .fl{display:inline-flex;align-items:center;gap:8px;margin-bottom:8px}
.footer .fl img{width:22px;height:22px;opacity:.4}
@media(max-width:760px){.grid2{grid-template-columns:1fr}.navbar{flex-direction:column;padding:14px}
.form-row{flex-direction:column}.now-title{font-size:16px}.logo .az{display:none}}
'''
@app.context_processor
def inject_globals():
    return {'css': BASE_CSS, 'ch_name': CHANNEL_NAME, 'ch_logo': CHANNEL_LOGO, 'ch_tag': CHANNEL_TAGLINE}

# ==================== INDEX ====================
INDEX_HTML = '''<!DOCTYPE html>
<html lang="az">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>📺 {{ ch_name }} — CANLI YAYIM</title>
<meta name="description" content="{{ ch_name }} — canlı yayım, efir proqramı və son xəbərlər. {{ ch_tag }}">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
<link rel="icon" href="{{ ch_logo }}">
<script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
<style>{{ css }}</style>
</head>
<body>
<div class="bg"></div>
<nav class="navbar">
  <div class="logo">
    <img src="{{ ch_logo }}" alt="logo">
    <span class="brand">{{ ch_name }}</span>
    <span class="az">{{ ch_tag }}</span>
  </div>
  <div class="nav-links">
    <a href="/" class="active"><i class="fas fa-home"></i> Ana</a>
    <a href="/#proqram"><i class="fas fa-calendar-alt"></i> Proqram</a>
    <a href="/#xeberler"><i class="fas fa-newspaper"></i> Xəbərlər</a>
    {% if session.get('logged_in') %}
    <a href="/admin"><i class="fas fa-cog"></i> Admin</a>
    <a href="/logout" class="logout-btn"><i class="fas fa-sign-out-alt"></i> Çıxış</a>
    {% else %}
    <a href="/login"><i class="fas fa-lock"></i> Giriş</a>
    {% endif %}
  </div>
</nav>
<div class="container">

  <!-- CANLI PLAYER -->
  <div class="player-shell">
    <div class="player-wrapper">
      <video id="videoPlayer" autoplay muted playsinline></video>
      <div class="overlay" id="loadingOv" style="display:flex"><div class="spinner"></div></div>
      <div class="overlay" id="offOv" style="display:none">
        <i class="fas fa-satellite-dish"></i>
        <div class="msg">📡 Yayım hazırda oflayndır — tezliklə davam edəcək</div>
      </div>
    </div>
  </div>
  <div class="player-info">
    <div class="now-title">
      <span class="live-dot"></span>
      <span class="name" id="nowName">Qoşulur...</span>
      <span class="badge-live">● CANLI</span>
    </div>
    <div class="meta">
      <span class="quality">HD 720p</span>
      <span><i class="fas fa-eye"></i> <b id="viewerCount">—</b></span>
    </div>
    <div style="display:flex;gap:8px">
      <button class="ctrl-btn" id="muteBtn" onclick="toggleMute()"><i class="fas fa-volume-xmark"></i> Səsi aç</button>
      <button class="ctrl-btn" onclick="fullscreen()"><i class="fas fa-expand"></i> Tam ekran</button>
    </div>
  </div>

  <div class="marquee"><span>🎬 {{ ch_name }} canlı yayımı · VLC / MX Player linki: {{ request.host_url }}live.m3u8 · {% for v in videos[:3] %}{{ v.name }} — efirdə · {% endfor %}Yaxşı baxışlar! 🎬</span></div>

  <!-- EFİR PROQRAMI + XƏBƏRLƏR (AZ TV üslubu) -->
  <div class="grid2" id="proqram">
    <div class="panel">
      <h2><i class="fas fa-calendar-alt"></i> EFİR PROQRAMI <span>BUGÜN</span></h2>
      {% for s in schedule %}
      <div class="sched-item {% if loop.first %}now{% endif %}">
        <span class="t">{{ s.time }}</span>
        <span class="n">{% if loop.first %}<i class="fas fa-play" style="font-size:9px;color:var(--gold);margin-right:6px"></i>{% endif %}{{ s.name }}</span>
      </div>
      {% else %}
      <div class="empty-state"><i class="fas fa-calendar"></i><p>Proqram hələ doldurulmayıb</p></div>
      {% endfor %}
    </div>
    <div class="panel" id="xeberler">
      <h2><i class="fas fa-newspaper"></i> SON XƏBƏRLƏR <span>{{ news|length }}</span></h2>
      {% for n in news[:5] %}
      <div class="news-item">
        <div class="d"><i class="far fa-clock"></i> {{ n.date }}</div>
        <div class="t">{{ n.title }}</div>
      </div>
      {% else %}
      <div class="empty-state"><i class="fas fa-newspaper"></i><p>Hələ xəbər yoxdur</p></div>
      {% endfor %}
    </div>
  </div>

  <!-- NÖVBƏ -->
  <div class="section-title"><i class="fas fa-list"></i> YAYIM NÖVBƏSİ <span>{{ videos|length }}</span></div>
  <div class="queue">
    {% for v in videos %}
    <div class="queue-item">
      <div class="qn"><span class="idx">#{{ loop.index }}</span>
        {% if loop.first %}<i class="fas fa-broadcast-tower" style="color:var(--gold);font-size:11px"></i>
        <span class="badge-live">EFİRDƏ</span>{% endif %}
        {{ v.name }}</div>
      <div class="qu">{{ v.url }}</div>
    </div>
    {% else %}
    <div class="empty-state"><i class="fas fa-satellite-dish"></i><p>Növbə boşdur</p></div>
    {% endfor %}
  </div>
</div>
<div class="footer">
  <div class="fl"><img src="{{ ch_logo }}" alt=""> {{ ch_name }} © 2026</div>
  <div>Bütün hüquqlar qorunur</div>
</div>

<script>
const video = document.getElementById('videoPlayer');
const loadingOv = document.getElementById('loadingOv');
const offOv = document.getElementById('offOv');
let hls = null, wasLive = false;

function show(el){ [loadingOv, offOv].forEach(o=>o.style.display='none'); if(el) el.style.display='flex'; }

function startLive(){
  show(loadingOv);
  if (hls) { hls.destroy(); hls = null; }
  if (Hls.isSupported()) {
    hls = new Hls({ lowLatencyMode: true, liveSyncDurationCount: 3 });
    hls.loadSource('/live.m3u8');
    hls.attachMedia(video);
    hls.on(Hls.Events.MANIFEST_PARSED, () => { show(null); video.play().catch(()=>{}); });
    hls.on(Hls.Events.ERROR, (e, data) => {
      if (data.fatal && data.type === Hls.ErrorTypes.NETWORK_ERROR) {
        show(offOv); hls.destroy(); hls = null; setTimeout(checkAndStart, 5000);
      } else if (data.fatal && data.type === Hls.ErrorTypes.MEDIA_ERROR) { hls.recoverMediaError(); }
    });
  } else {
    video.src = '/live.m3u8';
    video.play().catch(()=>{});
    video.onerror = () => { show(offOv); setTimeout(checkAndStart, 5000); };
    video.onplaying = () => show(null);
  }
}

function checkAndStart(){
  fetch('/api/live-status',{cache:'no-store'})
    .then(r=>r.json())
    .then(s=>{
      if (s.live) {
        if (s.now) document.getElementById('nowName').textContent = s.now;
        if (!wasLive) { wasLive = true; startLive(); }
      } else {
        wasLive = false; show(offOv);
        document.getElementById('nowName').textContent = 'Yayım yoxdur';
        setTimeout(checkAndStart, 5000);
      }
    })
    .catch(()=>setTimeout(checkAndStart, 5000));
}

setInterval(()=>{
  fetch('/api/live-status',{cache:'no-store'}).then(r=>r.json()).then(s=>{
    if (s.now) document.getElementById('nowName').textContent = s.now;
    document.getElementById('viewerCount').textContent = s.viewers;
  }).catch(()=>{});
}, 10000);

function toggleMute(){
  video.muted = !video.muted;
  document.getElementById('muteBtn').innerHTML = video.muted
    ? '<i class="fas fa-volume-xmark"></i> Səsi aç'
    : '<i class="fas fa-volume-high"></i> Səsi bağla';
}
document.addEventListener('click', function unlock(){
  if (video.muted && video.paused){ video.muted=false; video.play().catch(()=>{}); }
  document.removeEventListener('click', unlock);
});
function fullscreen(){
  const w = document.querySelector('.player-shell');
  if (w.requestFullscreen) w.requestFullscreen();
  else if (w.webkitRequestFullscreen) w.webkitRequestFullscreen();
}
checkAndStart();
</script>
</body>
</html>
'''

# ==================== LOGIN ====================
LOGIN_HTML = '''<!DOCTYPE html>
<html lang="az">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>🔐 Giriş — {{ ch_name }}</title>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
<link rel="icon" href="{{ ch_logo }}">
<style>{{ css }}</style>
</head>
<body>
<div class="bg"></div>
<div style="min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px">
  <div class="card" style="max-width:420px;width:100%;animation:fadeUp .6s ease">
    <div style="display:flex;flex-direction:column;align-items:center;gap:10px;margin-bottom:26px">
      <img src="{{ ch_logo }}" style="width:74px;height:74px;object-fit:contain;
        filter:drop-shadow(0 4px 18px rgba(255,215,0,.25))" alt="logo">
      <div class="logo" style="font-size:26px;justify-content:center"><span class="brand">{{ ch_name }}</span></div>
      <div style="color:rgba(255,255,255,.3);font-size:13px"><i class="fas fa-lock" style="color:var(--gold)"></i> Admin girişi</div>
    </div>
    {% if error %}<div class="message warning"><i class="fas fa-exclamation-circle"></i> {{ error }}</div>{% endif %}
    <form method="POST" class="form-row" style="flex-direction:column">
      <input type="text" name="username" placeholder="👤 İstifadəçi adı" required>
      <input type="password" name="password" placeholder="🔑 Şifrə" required>
      <button type="submit" class="btn btn-primary" style="width:100%;justify-content:center;padding:14px">
        <i class="fas fa-sign-in-alt"></i> Daxil ol</button>
    </form>
    <div style="text-align:center;margin-top:22px;color:rgba(255,255,255,.15);font-size:12px">© 2026 {{ ch_name }}</div>
  </div>
</div>
</body>
</html>
'''

# ==================== ADMIN ====================
ADMIN_HTML = '''<!DOCTYPE html>
<html lang="az">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>⚙️ Admin — {{ ch_name }}</title>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
<link rel="icon" href="{{ ch_logo }}">
<style>{{ css }}</style>
</head>
<body>
<div class="bg"></div>
<nav class="navbar">
  <div class="logo">
    <img src="{{ ch_logo }}" alt="logo">
    <span class="brand">{{ ch_name }}</span>
    <span class="az" style="font-size:11px;letter-spacing:2px">ADMIN PANEL</span>
  </div>
  <div class="nav-links">
    <a href="/"><i class="fas fa-home"></i> Ana</a>
    <a href="/admin" class="active"><i class="fas fa-cog"></i> Admin</a>
    <a href="/logout" class="logout-btn"><i class="fas fa-sign-out-alt"></i></a>
  </div>
</nav>
<div class="container">
  {% if message %}<div class="message {{ msg_type }}"><i class="fas fa-check-circle"></i> {{ message }}</div>{% endif %}

  <div class="stream-link">
    <i class="fas fa-broadcast-tower" style="color:#00ff88"></i>
    <b>Canlı yayım linki</b> — VLC, MX Player, Smart TV:
    <code>{{ request.host_url }}live.m3u8</code>
  </div>

  <div class="grid2" style="margin-top:0">
    <div class="panel">
      <h2><i class="fas fa-plus-circle"></i> Video Əlavə Et</h2>
      <form method="POST" class="form-row" style="flex-direction:column">
        <input type="hidden" name="action" value="add">
        <input type="text" name="name" placeholder="Video / veriliş adı" required>
        <input type="url" name="url" placeholder="m3u8 / mp4 linki" required>
        <button type="submit" class="btn btn-primary" style="justify-content:center"><i class="fas fa-plus"></i> Yayıma qoy</button>
      </form>
    </div>
    <div class="panel">
      <h2><i class="fas fa-calendar-plus"></i> Proqrama Veriliş Əlavə Et</h2>
      <form method="POST" class="form-row" style="flex-direction:column">
        <input type="hidden" name="action" value="add_sched">
        <input type="text" name="time" placeholder="Saat (məs: 20:00)" required>
        <input type="text" name="name" placeholder="Verilişin adı" required>
        <button type="submit" class="btn btn-primary" style="justify-content:center"><i class="fas fa-plus"></i> Proqrama qoy</button>
      </form>
    </div>
  </div>

  <div class="card">
    <h2><i class="fas fa-newspaper"></i> Xəbər / Elan Əlavə Et</h2>
    <form method="POST" class="form-row">
      <input type="hidden" name="action" value="add_news">
      <input type="text" name="title" placeholder="Xəbər mətni..." required style="flex:4">
      <button type="submit" class="btn btn-primary"><i class="fas fa-paper-plane"></i> Yayınla</button>
    </form>
  </div>

  <div class="card">
    <h2><i class="fas fa-list"></i> Yayım Növbəsi ({{ videos|length }})</h2>
    <div class="queue">
      {% for v in videos %}
      <div class="queue-item">
        <div class="qn"><span class="idx">#{{ loop.index }}</span>
          {% if loop.first %}<span class="badge-live">EFİRDƏ</span>{% endif %}{{ v.name }}</div>
        <div class="qu">{{ v.url }} · {{ v.added }}</div>
        <div style="display:flex;gap:6px;margin-left:auto">
          {% if not loop.first %}
          <form method="POST"><input type="hidden" name="action" value="up_video">
            <input type="hidden" name="video_id" value="{{ v.id }}">
            <button class="btn btn-outline btn-mini"><i class="fas fa-arrow-up"></i></button></form>
          {% endif %}
          <form method="POST"><input type="hidden" name="action" value="del_video">
            <input type="hidden" name="video_id" value="{{ v.id }}">
            <button type="submit" class="btn btn-danger btn-mini" onclick="return confirm('Silinsin?')">
              <i class="fas fa-trash"></i></button></form>
        </div>
      </div>
      {% else %}<div class="empty-state"><i class="fas fa-satellite-dish"></i><p>Növbə boşdur</p></div>{% endfor %}
    </div>
  </div>

  <div class="grid2" style="margin-top:0">
    <div class="panel">
      <h2><i class="fas fa-calendar-alt"></i> Efir Proqramı ({{ schedule|length }})</h2>
      {% for s in schedule %}
      <div class="sched-item">
        <span class="t">{{ s.time }}</span>
        <span class="n" style="flex:1">{{ s.name }}</span>
        <form method="POST"><input type="hidden" name="action" value="del_sched">
          <input type="hidden" name="sched_id" value="{{ s.id }}">
          <button type="submit" class="btn btn-danger btn-mini"><i class="fas fa-times"></i></button></form>
      </div>
      {% else %}<div class="empty-state"><i class="fas fa-calendar"></i><p>Boşdur</p></div>{% endfor %}
    </div>
    <div class="panel">
      <h2><i class="fas fa-newspaper"></i> Xəbərlər ({{ news|length }})</h2>
      {% for n in news %}
      <div class="news-item" style="display:flex;gap:10px;align-items:center">
        <div style="flex:1"><div class="d">{{ n.date }}</div><div class="t">{{ n.title }}</div></div>
        <form method="POST"><input type="hidden" name="action" value="del_news">
          <input type="hidden" name="news_id" value="{{ n.id }}">
          <button type="submit" class="btn btn-danger btn-mini"><i class="fas fa-times"></i></button></form>
      </div>
      {% else %}<div class="empty-state"><i class="fas fa-newspaper"></i><p>Boşdur</p></div>{% endfor %}
    </div>
  </div>
</div>
</body>
</html>
'''

if __name__ == '__main__':
    app.run(debug=False, host='0.0.0.0', port=int(os.environ.get('PORT', 5000)), threaded=True)
