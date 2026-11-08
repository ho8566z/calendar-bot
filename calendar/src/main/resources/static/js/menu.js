// 모바일 폭에서 접어 둔 상단 메뉴를 MENU 버튼으로 열고 닫는다(nfr-001).
// 메뉴를 보이고 숨기는 것은 CSS 가 body.menu_open 클래스로 정한다.
const MENU_MOBILE_MAX_WIDTH = 768;      // CSS 의 모바일 기준 폭과 같다

document.addEventListener('DOMContentLoaded', function () {
    let button = document.getElementById('menu_toggle');
    if (!button) {
        return;
    }

    function setMenuOpen(open) {
        document.body.classList.toggle('menu_open', open);
        button.setAttribute('aria-expanded', String(open));

    }

    button.addEventListener('click', function () {
        setMenuOpen(!document.body.classList.contains('menu_open'));
    });

    // Esc 로 닫기
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            setMenuOpen(false);
        }
    });

    // 화면을 넓혀 PC 배치로 돌아가면 열림 상태를 지운다(다시 좁아졌을 때 저절로 열려 있지 않게)
    window.addEventListener('resize', function () {
        if (window.innerWidth > MENU_MOBILE_MAX_WIDTH) {
            setMenuOpen(false);
        }
    });

});
