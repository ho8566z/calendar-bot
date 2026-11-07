document.addEventListener("DOMContentLoaded", function () {
    console.log('DOCUMENT READY!!');

    fetchGetUsers();

    initEvents();

});

async function fetchGetUsers() {
    console.log('fetchGetUsers() CALLED!!');

    try {
        let response = await fetch('/user/users', {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json; charset=utf-8'
            }
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        console.log('fetchGetUsers() COMMUNICATION SUCCESS!!');

        let data = await response.json();
        console.log('data: ', data);

        let users = data.users;
        for (let i = 0; i < users.length; i++) {
            let template = document.querySelector("#list-template").content.cloneNode(true);
            template.querySelector("tr").setAttribute("id", "user_" + users[i].no);
            template.querySelector(".no").textContent = users[i].no;
            template.querySelector(".id").textContent = users[i].id;

            template.querySelector("select[name='authority']").setAttribute("user_no", users[i].no);
            template.querySelector("select[name='authority']").value = users[i].userAuthorityDto.no;

            template.querySelector(".mail").textContent = users[i].mail;
            template.querySelector(".phone").textContent = users[i].phone;
            template.querySelector(".reg_date").textContent = users[i].reg_date;
            template.querySelector(".mod_date").textContent = users[i].mod_date;
            document.querySelector("#section_wrap tbody").appendChild(template);

        }

    } catch (error) {
        console.log('fetchGetUsers() COMMUNICATION ERROR!!', error);

    }

}

function initEvents() {
    console.log('initEvents() CALLED!!');

    document.querySelector("#section_wrap").addEventListener('change', function (event) {
        console.log("section_wrap CAHNGED!!");

        if (event.target.name === 'authority') {
            console.log('authority changed!!');

            let userNo = event.target.getAttribute('user_no');
            let authorityNo = event.target.value;

            fetchUpdateUserAuthority(userNo,authorityNo);

        }

    });

}

const UPDATE_USER_AUTHORITY_SUCCESS   = 1;
const UPDATE_USER_AUTHORITY_FAIL      = 0;

async function fetchUpdateUserAuthority(userNo, authorityNo) {
    console.log('fetchUpdateUserAuthority() CALLED!!');

    let reqData = JSON.stringify({
        'authorityNo' : authorityNo
    });

    try {
        let response = await fetch(`/user/${userNo}/auth`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: reqData
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        let data = await response.json();
        console.log('data: ', data);

        if (data.result === UPDATE_USER_AUTHORITY_SUCCESS) {
            alert('Authority change success!!!');
            document.querySelector(`#user_${userNo} .mod_date`).textContent = data.mod_date;

        } else {
            alert('Authority change fail!!!');

        }


    } catch (error) {
        console.log('fetchUpdateUserAuthority() COMMUNICATION ERROR!!', error);
        alert('Authority change fail!!!');

    }

}