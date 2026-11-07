const UPDATE_ADMIN_AUTHORITY_SUCCESS   = 1;
const UPDATE_ADMIN_AUTHORITY_FAIL      = 0;

document.addEventListener("DOMContentLoaded", function (){
    console.log('DOCUMENT READY!!');

    fetchGetAdmins();

    initEvents();

});

async function fetchGetAdmins() {
    console.log('fetchGetAdmins()');

    try {
        let response = await fetch('/admin/admins', {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json; charset=utf-8'
            }
        });

        if (!response.ok) {
            throw new Error('Network response was not ok!');
        }

        console.log('fetchGetAdmins() COMMUNICATION SUCCESS!!');

        let data = await response.json();
        console.log('data: ', data);
        let admins = data.admins
        for (let i = 0; admins.length; i++) {
            let template = document.querySelector('#list-template').content.cloneNode(true);
            template.querySelector('tr').setAttribute('id', 'admin_' + admins[i].no);
            template.querySelector('.no').textContent = admins[i].no;
            template.querySelector('.id').textContent = admins[i].id;
            template.querySelector('select[name="authority"]').setAttribute('admin_no', admins[i].no);
            template.querySelector('select[name="authority"]').value = admins[i].authorityDto.no;
            template.querySelector('.mail').textContent = admins[i].mail;
            template.querySelector('.phone').textContent = admins[i].phone;
            template.querySelector('.reg_date').textContent = admins[i].reg_date;
            template.querySelector('.mod_date').textContent = admins[i].mod_date;
            document.querySelector('#section_wrap tbody').appendChild(template);
        }

    } catch (error) {
        console.log('fetchGetAdmins() COMMUNICATION ERROR!!', error);

    }

}

function initEvents() {
    console.log('initEvents() CALLED!!');

    document.querySelector('#section_wrap').addEventListener('change', function (event) {
        console.log('section_wrap CHANGE EVENT!!');
        console.log('event: ', event);

        if (event.target.name === 'authority') {
            console.log('authority CHANGED!!');

            let adminNo = event.target.getAttribute('admin_no');
            let authorityNo =  event.target.value;

            console.log('adminNo: ', adminNo);
            console.log('authorityNo: ', authorityNo);

            fetchUpdateAdminAuthority(adminNo, authorityNo);

        }



    });

}

async function fetchUpdateAdminAuthority(adminNo, authorityNo) {
    console.log('fetchUpdateAdminAuthority() CALLEd!!');

    let reqData = JSON.stringify({
        'authorityNo': authorityNo
    });

    try {
        let response = await fetch(`/admin/${adminNo}/auth`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: reqData
        });

        if (!response.ok) {
            throw new Error('Network response was not ok!');
        }

        console.log('fetchUpdateAdminAuthority() COMMUNICATION SUCCESS!!');

        let data = await response.json();
        console.log('data: ', data);

        if (data.result === UPDATE_ADMIN_AUTHORITY_SUCCESS) {
            alert("Authority change success!!");
            document.querySelector(`#admin_${adminNo} .mod_date`).textContent = data.mod_date;

        } else {
            alert("Authority change fail!!");

        }

    } catch (error) {
        console.log('fetchUpdateAdminAuthority() COMMUNICATION ERROR!!', error);
        alert("Authority change fail!!");

    }

}