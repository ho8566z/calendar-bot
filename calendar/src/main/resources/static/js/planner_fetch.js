async function fetchWritePlan(year, month, date, title, body, file) {
    console.log('fetchWritePlan() CALLED!!');

    let formData = new FormData();
    formData.append("year", year);
    formData.append("month", month);
    formData.append("date", date);
    formData.append("title", title);
    formData.append("body", body);
    formData.append("file", file);

    try {
        let response = await fetch('/planner/plan', {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        console.log('fetchWritePlan() COMMUNICATION SUCCESS!! ');

        let data = await response.json();
        console.log('data: ', data);

        if (!data || data.result <= 0) {
            alert("일정 등록에 문제가 발생 했습니다.");

        } else {
            alert("일정이 정상적으로 등록 되었습니다.");
            removeCalenderTr();     // 기존 달력(<tr>) 제거
            addCalenderTr();        // 새 달력(<tr>) 생성
            fetchGetCurrentMonthPlans(); // 일정들 가져오는 함수

        }


    } catch (error) {
        console.log('fetchWritePlan() COMMUNICATION ERROR!! ', error);
        alert('일정 등록에 문제가 발생 했습니다.');

    } finally {
        hideWritePlanView();

    }

}

async function fetchGetCurrentMonthPlans() {
    console.log('fetchGetCurrentMonthPlans() CALLED!!');

    let reqData = {
        "year": current_year,
        "month": current_month + 1
    }

    let queryString = new URLSearchParams(reqData).toString();
    console.log("queryString: ", queryString);

    try {
        let response = await fetch(`/planner/plans?${queryString}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json; charset=utf-8'
            }
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        console.log('fetchGetCurrentMonthPlans() COMMUNICATION SUCCESS!! ');

        let data = await response.json();
        console.log('data: ', data);

        let plans = data.plans;
        plans.forEach(dto => {
            let appendTag = `<li><a class="title" href="#none" data-no="${dto.no}">${dto.title}</a></li>`;
            let targetElement = document.querySelector(`#date_${dto.date} ul.plan`);
            // targetElement.appendChild(appendTag);
            if (targetElement) {
                targetElement.insertAdjacentHTML('beforeend', appendTag);
            }

        });

    } catch (error) {
        console.log('fetchGetCurrentMonthPlans() COMMUNICATION ERROR!! ', error);

    }

}

async function fetchGetPlan(no) {
    console.log("fetchGetPlan() CALLED!!");

    let queryString = new URLSearchParams({"no": no}).toString();
    console.log("queryString: ", queryString);

    try {
        let response = await fetch(`/planner/plan?${queryString}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json; charset=utf-8'
            }
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        console.log('fetchGetPlan() COMMUNICATION SUCCESS!! ');

        let data = await response.json();
        console.log('data: ', data);

        let plan = data.plan;
        showDetailPlanView(plan);

    } catch (error) {
        console.log('fetchGetPlan() COMMUNICATION ERROR!! ', error);

    }

}

async function fetchRemovePlan(no) {
    console.log('fetchRemovePlan() CALLED!!');

    try {
        let response = await fetch(`/planner/plan/${no}`, {   // /planner/plan/2
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json; charset=utf-8'
            }
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        console.log('fetchRemovePlan() COMMUNICATION SUCCESS!! ');

        let data = await response.json();
        console.log('data: ', data);

        if (data.result > 0) {
            alert("일정이 정상적으로 삭제 되었습니다.");

            removeCalenderTr();
            addCalenderTr();
            fetchGetCurrentMonthPlans();

        } else {
            alert("일정이 정상적으로 삭제 되지 않았습니다.");

        }

    } catch (error) {
        console.log('fetchRemovePlan() COMMUNICATION ERROR!! ', error);

    } finally {
        hideDetailPlanView();

    }

}

async function fetchModifyPlan(no, year, month, date, title, body, file) {
    console.log('fetchModifyPlan() CALLED!!');

    let formData = new FormData();
    formData.append('year', year);
    formData.append('month', month);
    formData.append('date', date);
    formData.append('title', title);
    formData.append('body', body);

    if (file != null || file != undefined) {
        formData.append('file', file);
    }

    try {
        let response = await fetch(`/planner/plan/${no}`, {   // /planner/plan/2
            method: 'PUT',
            body: formData
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        console.log('fetchModifyPlan() COMMUNICATION SUCCESS!! ');

        let data = await response.json();
        console.log('data: ', data);

        if (!data || data.result <= 0) {
            alert('일정 수정에 문제가 발생했습니다.');

        } else {
            alert('일정이 정상적으로 수정 되었습니다.');

        }

        removeCalenderTr();
        addCalenderTr();
        fetchGetCurrentMonthPlans();

    } catch (error) {
        console.log('fetchModifyPlan() COMMUNICATION ERROR!! ', error);
        alert('일정 수정에 문제가 발생했습니다.');

    } finally {
        hideDetailPlanView();

    }

}