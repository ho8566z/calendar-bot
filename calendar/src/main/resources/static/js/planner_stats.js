// 월별 통계 화면(plan-010): 요일별·주별 일정 수를 표와 막대로 보여 준다. 값은 모두 textContent 로 넣는다.
const STATS_MIN_YEAR = 2025;
const STATS_MAX_YEAR = 2030;
const WEEKDAY_NAMES = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'];

let stats_year;
let stats_month;
let stats_seq = 0;      // 요청 번호(늦게 도착한 이전 응답이 새 결과를 덮어쓰지 않게 한다)

document.addEventListener('DOMContentLoaded', function () {
    console.log('STATS READY!!');

    // 처음 보여 줄 달: 주소의 year, month 가 범위 안이면 그 달, 아니면 이번 달(범위 밖이면 가까운 끝)
    let params = new URLSearchParams(window.location.search);
    let year = parseInt(params.get('year'), 10);
    let month = parseInt(params.get('month'), 10);

    if (!isValidPeriod(year, month)) {
        let now = new Date();
        year = now.getFullYear();
        month = now.getMonth() + 1;

        if (year < STATS_MIN_YEAR) { year = STATS_MIN_YEAR; month = 1; }
        if (year > STATS_MAX_YEAR) { year = STATS_MAX_YEAR; month = 12; }

    }

    setPeriod(year, month);

    document.getElementById('btn_pre').addEventListener('click', function () { movePeriod(-1); });
    document.getElementById('btn_next').addEventListener('click', function () { movePeriod(1); });
    document.querySelector('select[name="s_year"]').addEventListener('change', onSelectChanged);
    document.querySelector('select[name="s_month"]').addEventListener('change', onSelectChanged);

});

function isValidPeriod(year, month) {
    return Number.isInteger(year) && Number.isInteger(month)
            && year >= STATS_MIN_YEAR && year <= STATS_MAX_YEAR && month >= 1 && month <= 12;

}

// 보여 줄 달을 정하고 화면과 데이터를 갱신한다
function setPeriod(year, month) {
    stats_year = year;
    stats_month = month;

    document.querySelector('select[name="s_year"]').value = year;
    document.querySelector('select[name="s_month"]').value = month;
    document.getElementById('btn_pre').disabled = (year === STATS_MIN_YEAR && month === 1);
    document.getElementById('btn_next').disabled = (year === STATS_MAX_YEAR && month === 12);

    fetchMonthlyStats(year, month);

}

function movePeriod(delta) {
    let moved = new Date(stats_year, stats_month - 1 + delta, 1);
    let year = moved.getFullYear();
    let month = moved.getMonth() + 1;

    if (isValidPeriod(year, month)) {
        setPeriod(year, month);
    }

}

function onSelectChanged() {
    let year = parseInt(document.querySelector('select[name="s_year"]').value, 10);
    let month = parseInt(document.querySelector('select[name="s_month"]').value, 10);

    setPeriod(year, month);

}

async function fetchMonthlyStats(year, month) {
    console.log('fetchMonthlyStats() CALLED!!');

    let seq = ++stats_seq;
    let message = document.getElementById('message');
    message.textContent = '';

    try {
        let queryString = new URLSearchParams({"year": year, "month": month}).toString();
        let response = await fetch(`/planner/stats/monthly?${queryString}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json; charset=utf-8'
            }
        });

        if (!response.ok) {
            throw new Error(response.status === 400 ? 'INVALID_PERIOD' : 'Network response was not ok');
        }

        let data = await response.json();

        if (seq !== stats_seq) {
            return;     // 더 최근에 요청한 조회가 있다
        }

        renderStats(data);

    } catch (error) {
        console.log('fetchMonthlyStats() COMMUNICATION ERROR!! ', error);

        if (seq === stats_seq) {
            clearStats();
            message.textContent = (error.message === 'INVALID_PERIOD')
                    ? '통계를 조회할 수 없는 기간입니다.'
                    : '통계를 불러오지 못했습니다.';
        }

    }

}

// 해당 주(1부터)의 날짜 범위. 서버의 계산과 같다(1일의 요일을 기준으로 달력의 한 줄).
function weekRange(year, month, week) {
    let offset = new Date(year, month - 1, 1).getDay();
    let lastDay = new Date(year, month, 0).getDate();

    return {
        start: Math.max(1, (week - 1) * 7 - offset + 1),
        end: Math.min(lastDay, week * 7 - offset)
    };

}

function clearStats() {
    document.getElementById('total_count').textContent = '0';
    document.getElementById('busiest_text').textContent = '';
    document.getElementById('weekday_rows').innerHTML = '';
    document.getElementById('week_rows').innerHTML = '';

}

function renderStats(data) {
    console.log('renderStats() CALLED!!');

    document.getElementById('total_count').textContent = data.total;

    let busiest = data.busiest_week;
    document.getElementById('busiest_text').textContent = busiest
            ? `일정이 가장 많은 주: ${busiest.week}주차 (${data.month}월 ${busiest.start_date}일 ~ ${busiest.end_date}일, ${busiest.count}건)`
            : '이 달에는 일정이 없습니다.';

    renderRows('weekday_rows', data.by_weekday.map((count, i) => ({
        cells: [{cls: 'label', text: WEEKDAY_NAMES[i]}],
        count: count
    })));

    renderRows('week_rows', data.by_week.map((count, i) => {
        let range = weekRange(data.year, data.month, i + 1);
        return {
            cells: [
                {cls: 'label', text: `${i + 1}주차`},
                {cls: 'range', text: `${range.start}일 ~ ${range.end}일`}
            ],
            count: count,
            busiest: busiest && busiest.week === i + 1
        };
    }));

}

// 표의 줄들을 만든다. 막대는 가장 큰 값을 100%로 둔 비율이다.
function renderRows(tbodyId, rows) {
    let tbody = document.getElementById(tbodyId);
    tbody.innerHTML = '';

    let max = Math.max(1, ...rows.map(r => r.count));

    rows.forEach(row => {
        let tr = document.createElement('tr');
        if (row.busiest) {
            tr.className = 'busiest';
        }

        row.cells.forEach(cell => {
            let td = document.createElement('td');
            td.className = cell.cls;
            td.textContent = cell.text;
            tr.appendChild(td);
        });

        let countTd = document.createElement('td');
        countTd.className = 'count';
        countTd.textContent = row.count;
        tr.appendChild(countTd);

        let barTd = document.createElement('td');
        let bar = document.createElement('div');
        bar.className = 'bar';
        let fill = document.createElement('span');
        fill.style.width = (row.count / max * 100) + '%';
        bar.appendChild(fill);
        barTd.appendChild(bar);
        tr.appendChild(barTd);

        tbody.appendChild(tr);
    });

}
