// 관리자 사용자 관리 화면: 목록·검색·페이지 이동, 승인·승인 취소
// 아이디·메일 같은 사용자 입력이 들어가므로 화면에는 textContent만 쓴다(innerHTML 금지).
(function () {
    'use strict';

    const PAGE_SIZE = 20;

    const state = {keyword: '', page: 0, total: 0};

    let rows, message, pageInfo, btnPrev, btnNext, searchForm, template;

    document.addEventListener('DOMContentLoaded', function () {
        rows = document.getElementById('user_rows');
        message = document.getElementById('message');
        pageInfo = document.getElementById('page_info');
        btnPrev = document.getElementById('btn_prev');
        btnNext = document.getElementById('btn_next');
        searchForm = document.getElementById('search_form');
        template = document.getElementById('row_template');

        searchForm.addEventListener('submit', function (event) {
            event.preventDefault();
            state.keyword = searchForm.elements['keyword'].value.trim();
            state.page = 0;
            loadUsers();
        });

        btnPrev.addEventListener('click', function () {
            if (state.page > 0) {
                state.page -= 1;
                loadUsers();
            }
        });

        btnNext.addEventListener('click', function () {
            if (state.page + 1 < totalPages()) {
                state.page += 1;
                loadUsers();
            }
        });

        // 승인·승인 취소 버튼(행이 다시 그려져도 동작하도록 tbody에서 이벤트를 받는다)
        rows.addEventListener('click', function (event) {
            const button = event.target.closest('button[data-action]');
            if (!button) {
                return;
            }
            const tr = button.closest('tr');
            const action = button.dataset.action;

            if (action === 'unapprove' && !window.confirm(tr.dataset.id + ' 님의 승인을 취소할까요?')) {
                return;
            }
            changeRole(tr, action, button);
        });

        loadUsers();
    });

    function totalPages() {
        return Math.max(1, Math.ceil(state.total / PAGE_SIZE));
    }

    function showMessage(text, type) {
        message.textContent = text || '';
        message.className = 'message' + (type ? ' ' + type : '');
    }

    // 목록 조회
    async function loadUsers() {
        const params = new URLSearchParams({
            keyword: state.keyword,
            page: String(state.page),
            size: String(PAGE_SIZE)
        });

        try {
            const response = await fetch('/admin/users/data?' + params.toString(), {
                method: 'GET',
                headers: {'Accept': 'application/json'}
            });

            // 로그인이 풀리면 로그인 화면(HTML)으로 넘어가므로 JSON이 아닌 응답은 오류로 본다
            if (!response.ok || response.redirected) {
                throw new Error('목록 응답 오류 ' + response.status);
            }
            const data = await response.json();

            state.total = data.total;
            renderRows(data.users);
            renderPager();

        } catch (error) {
            console.error('loadUsers() ERROR', error);
            showMessage('목록을 불러오지 못했습니다. 다시 로그인한 뒤 시도해 주세요.', 'error');
        }
    }

    function renderRows(users) {
        rows.replaceChildren();

        if (!users || users.length === 0) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.className = 'empty';
            td.colSpan = 8;
            td.textContent = state.keyword ? '검색 결과가 없습니다.' : '회원이 없습니다.';
            tr.appendChild(td);
            rows.appendChild(tr);
            return;
        }

        for (const user of users) {
            const fragment = template.content.cloneNode(true);
            const tr = fragment.querySelector('tr');
            tr.dataset.no = user.no;
            tr.dataset.id = user.id;

            fragment.querySelector('.no').textContent = user.no;
            fragment.querySelector('.id').textContent = user.id;
            fragment.querySelector('.mail').textContent = user.mail;
            fragment.querySelector('.phone').textContent = user.phone;
            fragment.querySelector('.reg_date').textContent = user.reg_date;
            fragment.querySelector('.mod_date').textContent = user.mod_date;
            setRole(fragment, user.role);

            rows.appendChild(fragment);
        }
    }

    // 역할 칸과 버튼을 역할에 맞게 바꾼다. 승인은 PRE_USER만, 승인 취소는 USER만, ADMIN은 버튼 없음
    function setRole(scope, role) {
        const roleCell = scope.querySelector('.role');
        roleCell.textContent = role || '-';
        roleCell.className = 'role' + (role ? ' ' + role : '');

        const actionCell = scope.querySelector('.action');
        actionCell.replaceChildren();

        if (role === 'PRE_USER') {
            actionCell.appendChild(createButton('승인', 'approve'));
        } else if (role === 'USER') {
            actionCell.appendChild(createButton('승인 취소', 'unapprove'));
        } else {
            actionCell.textContent = '-';
        }
    }

    function createButton(label, action) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = label;
        button.dataset.action = action;
        return button;
    }

    function renderPager() {
        pageInfo.textContent = (state.page + 1) + ' / ' + totalPages() + ' (총 ' + state.total + '명)';
        btnPrev.disabled = state.page <= 0;
        btnNext.disabled = state.page + 1 >= totalPages();
    }

    // 승인·승인 취소
    async function changeRole(tr, action, button) {
        const no = tr.dataset.no;
        const id = tr.dataset.id;
        button.disabled = true;     // 연속 클릭 방지

        try {
            const response = await fetch('/admin/users/' + encodeURIComponent(no) + '/' + action, {
                method: 'POST',
                headers: {'Accept': 'application/json'}
            });

            if (response.redirected) {
                throw new Error('로그인이 필요합니다');
            }
            const data = await response.json();

            if (response.ok && data.result === 1) {
                tr.querySelector('.mod_date').textContent = data.mod_date;
                setRole(tr, data.role);
                showMessage(action === 'approve'
                    ? id + ' 님을 승인했습니다. 사용자가 다시 로그인하면 일정 기능을 쓸 수 있습니다.'
                    : id + ' 님의 승인을 취소했습니다. 이미 로그인한 사용자는 로그아웃 전까지 기존 권한이 유지됩니다.', 'ok');
                return;
            }

            if (response.status === 404) {
                showMessage('회원을 찾을 수 없어 목록을 새로 불러왔습니다.', 'error');
            } else if (response.status === 409) {
                showMessage(id + ' 님은 이미 다른 상태입니다(현재 ' + (data.role || '-') + '). 목록을 새로 불러왔습니다.', 'error');
            } else {
                showMessage('처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요.', 'error');
                button.disabled = false;
                return;
            }
            loadUsers();

        } catch (error) {
            console.error('changeRole() ERROR', error);
            showMessage('처리하지 못했습니다. 다시 로그인한 뒤 시도해 주세요.', 'error');
            button.disabled = false;
        }
    }

})();
