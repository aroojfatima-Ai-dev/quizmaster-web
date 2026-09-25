/*
 * Take-a-test helpers:
 *  - the question palette jumps to another question without losing the answer
 *    currently selected on screen (it submits the same form),
 *  - submitting warns about unanswered questions,
 *  - the auto-submit path can inject the action the form needs.
 */
(function () {
    'use strict';

    var form = document.getElementById('quizForm');
    if (!form) {
        return;
    }

    function hiddenField(name, value) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        return input;
    }

    // Palette: submit the form with action=goto so the chosen radio is saved.
    form.querySelectorAll('.palette a').forEach(function (link) {
        link.addEventListener('click', function (event) {
            event.preventDefault();
            form.appendChild(hiddenField('action', 'goto'));
            form.appendChild(hiddenField('q', link.getAttribute('data-goto')));
            form.submit();
        });
    });

    // Warn before submitting with questions left blank.
    var submitButton = document.getElementById('submitButton');
    if (submitButton) {
        submitButton.addEventListener('click', function (event) {
            var total = parseInt(form.getAttribute('data-total') || '0', 10);
            var answered = form.querySelectorAll('input[name="choice"]:checked').length;
            if (answered < total) {
                var missing = total - answered;
                var message = 'You have ' + missing + (missing === 1 ? ' unanswered question' : ' unanswered questions')
                    + ' on this test. Submit anyway?';
                if (!window.confirm(message)) {
                    event.preventDefault();
                }
            }
        });
    }
})();
