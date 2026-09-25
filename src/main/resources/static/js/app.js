/*
 * Small progressive enhancements: dismissing alerts, copying a class code,
 * confirming destructive actions. Every screen works without JavaScript.
 */
(function () {
    'use strict';

    // "Copy" buttons next to class codes.
    document.querySelectorAll('[data-copy]').forEach(function (button) {
        button.addEventListener('click', function () {
            var value = button.getAttribute('data-copy');
            var done = function () {
                var original = button.textContent;
                button.textContent = 'Copied';
                window.setTimeout(function () {
                    button.textContent = original;
                }, 1600);
            };
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(value).then(done, function () {
                    window.prompt('Copy this code:', value);
                });
            } else {
                window.prompt('Copy this code:', value);
            }
        });
    });

    // Forms that should ask before doing something irreversible.
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            var message = form.getAttribute('data-confirm');
            if (message && !window.confirm(message)) {
                event.preventDefault();
            }
        });
    });

    // Alerts disappear on their own, but stay when the page is reloaded.
    document.querySelectorAll('.alert[data-autodismiss]').forEach(function (alert) {
        window.setTimeout(function () {
            alert.style.transition = 'opacity .4s ease';
            alert.style.opacity = '0';
            window.setTimeout(function () {
                alert.remove();
            }, 400);
        }, 6000);
    });

    // Guard the "Finish & Save Test" / "Confirm & Save All" buttons against a
    // double click creating the same test twice.
    document.querySelectorAll('form[data-one-shot]').forEach(function (form) {
        form.addEventListener('submit', function () {
            var buttons = form.querySelectorAll('button[type="submit"]');
            window.setTimeout(function () {
                buttons.forEach(function (button) {
                    button.disabled = true;
                    button.style.opacity = '0.75';
                });
            }, 0);
        });
    });
})();
