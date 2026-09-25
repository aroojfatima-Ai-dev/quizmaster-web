/*
 * The countdown for a test attempt.
 *
 * The deadline comes from the server (the attempt's start time plus the test
 * duration), so the display cannot be talked into extra time by editing the
 * page: the server re-checks the same deadline when an answer arrives, and
 * grades an overdue attempt with auto-submit enabled.
 *
 * While the countdown runs it shows "⏱ Time Remaining: mm:ss" in the app's sky
 * blue. Once it reaches zero, a test that allows overtime switches to
 * "⚠️ Overtime: +mm:ss" in red and keeps counting; an auto-submit test submits
 * itself.
 */
(function () {
    'use strict';

    var timer = document.getElementById('quizTimer');
    if (!timer) {
        return;
    }

    var deadline = parseInt(timer.getAttribute('data-deadline'), 10);
    var allowOvertime = timer.getAttribute('data-overtime') === 'true';
    var autoSubmit = timer.getAttribute('data-auto-submit') === 'true';
    var form = document.getElementById('quizForm');
    var submitted = false;

    function pad(value) {
        return (value < 10 ? '0' : '') + value;
    }

    function format(totalSeconds) {
        var minutes = Math.floor(totalSeconds / 60);
        var seconds = totalSeconds % 60;
        return pad(minutes) + ':' + pad(seconds);
    }

    function submitForStudent() {
        if (submitted || !form || !autoSubmit) {
            return;
        }
        submitted = true;
        timer.textContent = '⏱ Time is up - submitting your answers…';
        // form.submit() does not include any submit button, so the action the
        // server expects is added explicitly.
        var action = document.createElement('input');
        action.type = 'hidden';
        action.name = 'action';
        action.value = 'submit';
        form.appendChild(action);
        form.submit();
    }

    function render() {
        var remaining = Math.round((deadline - Date.now()) / 1000);

        if (remaining > 0) {
            timer.textContent = '⏱ Time Remaining: ' + format(remaining);
            timer.classList.remove('timer--overtime');
            return;
        }

        if (!allowOvertime) {
            submitForStudent();
            if (!submitted) {
                timer.textContent = '⏱ Time Remaining: 00:00';
                timer.classList.remove('timer--overtime');
            }
            return;
        }

        timer.textContent = '⚠️ Overtime: +' + format(-remaining);
        timer.classList.add('timer--overtime');
    }

    render();
    window.setInterval(render, 500);
})();
