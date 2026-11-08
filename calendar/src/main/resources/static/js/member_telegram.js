// 내 정보 화면의 텔레그램 연동·주간 브리핑 설정. 서버가 준 값은 모두 textContent 로만 넣는다.
const TG_POLL_INTERVAL_MS = 3000;            // 연동이 끝났는지 확인하는 간격
const TG_LINK_WAIT_MS = 10 * 60 * 1000;      // 연동 코드 유효 시간(10분)만큼만 확인한다
const TG_LINK_PREFIX = 'https://t.me/';      // 서버가 준 링크가 이 주소로 시작할 때만 쓴다

let tg_poll_timer = null;
let tg_poll_deadline = 0;
let tg_status_seq = 0;      // 상태 조회 번호(늦게 도착한 이전 응답이 새 결과를 덮어쓰지 않게 한다)
let tg_waiting = false;     // 연동 링크를 보여 주고 완료를 기다리는 중인지

document.addEventListener('DOMContentLoaded', function () {
    let root = document.getElementById('telegram');
    if (!root) {
        return;     // USER 가 아니면 이 영역이 화면에 없다
    }

    document.getElementById('tg_link_btn').addEventListener('click', onTelegramLink);
    document.getElementById('tg_brief_toggle').addEventListener('change', onBriefToggle);
    document.getElementById('tg_send_btn').addEventListener('click', onSendNow);
    document.getElementById('tg_unlink_btn').addEventListener('click', onUnlink);

    loadTelegramStatus();

});

// JSON 요청. 네트워크 오류는 status 0 으로 돌려준다
async function tgApi(method, url, body) {
    try {
        let options = {method: method, headers: {}};
        if (body !== undefined) {
            options.headers['Content-Type'] = 'application/json; charset=utf-8';
            options.body = JSON.stringify(body);
        }
        let response = await fetch(url, options);
        let data = {};
        try {
            data = await response.json();
        } catch (e) {
            data = {};
        }
        return {ok: response.ok, status: response.status, data: data};

    } catch (error) {
        return {ok: false, status: 0, data: {}};
    }

}

function setTgMessage(text, kind) {
    let el = document.getElementById('tg_message');
    el.textContent = text || '';
    el.className = 'tg_message' + (kind ? ' ' + kind : '');

}

function setTgBusy(ids, busy) {
    ids.forEach(id => { document.getElementById(id).disabled = busy; });

}

// 연동 상태를 가져와 화면에 반영한다
async function loadTelegramStatus() {
    let seq = ++tg_status_seq;
    let r = await tgApi('GET', '/member/telegram');
    if (seq !== tg_status_seq) {
        return;
    }
    if (!r.ok) {
        setTgMessage('연동 상태를 불러오지 못했습니다.', 'error');
        return;
    }

    let wasWaiting = tg_waiting;
    renderTelegram(r.data);
    if (wasWaiting && r.data.linked) {
        setTgMessage('텔레그램이 연동되었습니다. 아래에서 브리핑 수신을 켜 주세요.', 'ok');
    }

}

function renderTelegram(status) {
    let linked = !!status.linked;

    document.getElementById('tg_unlinked').hidden = linked;
    document.getElementById('tg_linked').hidden = !linked;
    document.getElementById('tg_brief_toggle').checked = linked && !!status.brief_enabled;

    if (linked) {
        stopTgPolling();
        document.getElementById('tg_pending').hidden = true;
        tg_waiting = false;
    }

}

// 연동 코드를 받아 봇 링크를 보여 주고, 연동이 끝날 때까지 확인한다
async function onTelegramLink() {
    setTgBusy(['tg_link_btn'], true);
    setTgMessage('');
    let r = await tgApi('POST', '/member/telegram/link');
    setTgBusy(['tg_link_btn'], false);

    if (r.ok) {
        let link = String(r.data.link || '');
        if (!link.startsWith(TG_LINK_PREFIX)) {
            setTgMessage('연동 링크를 받지 못했습니다.', 'error');
            return;
        }

        let anchor = document.getElementById('tg_link_anchor');
        anchor.href = link;
        anchor.textContent = link;
        document.getElementById('tg_pending').hidden = false;
        setTgMessage('링크를 눌러 텔레그램에서 시작 버튼을 눌러 주세요. (만료: ' + (r.data.expires_at || '') + ')');
        startTgPolling();

    } else if (r.data && r.data.code === 'ALREADY_LINKED') {
        loadTelegramStatus();

    } else if (r.data && r.data.code === 'TELEGRAM_NOT_CONFIGURED') {
        setTgMessage('서버에 텔레그램 봇이 설정되지 않았습니다.', 'error');

    } else {
        setTgMessage('연동 코드를 만들지 못했습니다.', 'error');
    }

}

function startTgPolling() {
    stopTgPolling();
    tg_waiting = true;
    tg_poll_deadline = Date.now() + TG_LINK_WAIT_MS;
    tg_poll_timer = setInterval(function () {
        if (Date.now() > tg_poll_deadline) {
            stopTgPolling();
            tg_waiting = false;
            document.getElementById('tg_pending').hidden = true;
            setTgMessage('연동 코드가 만료되었습니다. 다시 연동하기를 눌러 주세요.', 'error');
            return;
        }
        loadTelegramStatus();
    }, TG_POLL_INTERVAL_MS);

}

function stopTgPolling() {
    if (tg_poll_timer !== null) {
        clearInterval(tg_poll_timer);
        tg_poll_timer = null;
    }

}

// 브리핑 수신 켜기/끄기
async function onBriefToggle() {
    let toggle = document.getElementById('tg_brief_toggle');
    let desired = toggle.checked;

    setTgBusy(['tg_brief_toggle'], true);
    let r = await tgApi('PUT', '/member/brief', {enabled: desired});
    setTgBusy(['tg_brief_toggle'], false);

    if (r.ok) {
        setTgMessage(desired ? '주간 브리핑을 켰습니다.' : '주간 브리핑을 껐습니다.', 'ok');
        return;
    }

    toggle.checked = !desired;      // 저장되지 않았으니 원래 상태로 되돌린다
    if (r.data && r.data.code === 'TELEGRAM_NOT_LINKED') {
        setTgMessage('텔레그램을 먼저 연동해 주세요.', 'error');
        loadTelegramStatus();
    } else {
        setTgMessage('설정을 저장하지 못했습니다.', 'error');
    }

}

// 다음 주 브리핑을 지금 본인에게 보내 본다
async function onSendNow() {
    setTgBusy(['tg_send_btn'], true);
    setTgMessage('');
    let r = await tgApi('POST', '/member/brief/send');
    setTgBusy(['tg_send_btn'], false);

    if (r.ok) {
        setTgMessage(r.data.empty
                ? '다음 주에는 등록된 일정이 없어 안내 메시지를 보냈습니다.'
                : '다음 주 일정을 텔레그램으로 보냈습니다.', 'ok');
        return;
    }

    let code = r.data && r.data.code;
    if (code === 'TOO_FREQUENT') {
        setTgMessage((r.data.retry_after || 10) + '초 뒤에 다시 시도해 주세요.', 'error');
    } else if (code === 'BOT_BLOCKED') {
        setTgMessage('텔레그램에서 봇을 차단했는지 확인해 주세요.', 'error');
    } else if (code === 'TELEGRAM_NOT_LINKED') {
        setTgMessage('텔레그램을 먼저 연동해 주세요.', 'error');
        loadTelegramStatus();
    } else if (code === 'TELEGRAM_NOT_CONFIGURED') {
        setTgMessage('서버에 텔레그램 봇이 설정되지 않았습니다.', 'error');
    } else {
        setTgMessage('텔레그램으로 보내지 못했습니다.', 'error');
    }

}

// 연동 해제(수신도 함께 꺼진다)
async function onUnlink() {
    if (!window.confirm('텔레그램 연동을 해제할까요? 주간 브리핑 수신도 꺼집니다.')) {
        return;
    }

    setTgBusy(['tg_unlink_btn'], true);
    let r = await tgApi('DELETE', '/member/telegram');
    setTgBusy(['tg_unlink_btn'], false);

    if (r.ok) {
        renderTelegram(r.data);
        setTgMessage('텔레그램 연동을 해제했습니다.', 'ok');
    } else {
        setTgMessage('연동을 해제하지 못했습니다.', 'error');
    }

}
