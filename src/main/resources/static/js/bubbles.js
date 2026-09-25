/*
 * Animated water-bubble background.
 *
 * Same behaviour as the desktop app's bubble layer: 42 circles in the side
 * margins (2-28% and 72-98% of the width, so the centre card stays clear),
 * radius 5-20, drifting upwards with a slow sine wobble and a twinkle.
 *
 * One shared animation loop drives every bubble - the desktop version first
 * created one timer per bubble (42 timers, ~1,400 callbacks a second); this is
 * the web equivalent of the fix, a single requestAnimationFrame loop.
 */
(function () {
    'use strict';

    var BUBBLE_COUNT = 42;
    var TICK_MS = 30;              // the desktop timeline's 30ms frame
    var MAX_WOBBLE = 14;           // ±14px horizontal sine wobble

    var layer = document.querySelector('.bubble-layer');
    if (!layer) {
        return;
    }

    var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var bubbles = [];

    for (var i = 0; i < BUBBLE_COUNT; i++) {
        var bubble = document.createElement('span');
        bubble.className = 'bubble';

        var onLeft = i % 2 === 0;
        var relX = onLeft ? 0.02 + Math.random() * 0.26 : 0.72 + Math.random() * 0.26;
        var relY = 0.02 + Math.random() * 0.96;
        var radius = 5 + Math.random() * 15;

        bubble.style.width = (radius * 2) + 'px';
        bubble.style.height = (radius * 2) + 'px';
        bubble.style.left = (relX * 100) + '%';
        bubble.style.top = (relY * 100) + '%';
        bubble.style.animationDuration = (1.4 + Math.random() * 2.2) + 's';
        bubble.style.animationDelay = (-Math.random() * 2) + 's';

        layer.appendChild(bubble);

        bubbles.push({
            element: bubble,
            relY: relY,
            speed: 0.4 + Math.random() * 0.8,      // pixels per 30ms tick
            wobbleSpeed: 0.02 + Math.random() * 0.04,
            waveAngle: Math.random() * Math.PI * 2,
            offsetY: 0
        });
    }

    if (reduceMotion) {
        return;
    }

    var last = performance.now();
    var accumulator = 0;

    function frame(now) {
        var elapsed = now - last;
        last = now;
        // Advance in fixed 30ms steps so the motion is identical on any device.
        accumulator += Math.min(elapsed, 250);

        while (accumulator >= TICK_MS) {
            accumulator -= TICK_MS;
            var height = layer.clientHeight || window.innerHeight;

            for (var index = 0; index < bubbles.length; index++) {
                var state = bubbles[index];
                state.offsetY -= state.speed;

                var anchor = height * state.relY;
                if (state.offsetY < -(anchor + 40)) {
                    state.offsetY = height * (1 - state.relY) + 40;
                }
                state.waveAngle += state.wobbleSpeed;

                state.element.style.transform =
                    'translate(' + (Math.sin(state.waveAngle) * MAX_WOBBLE).toFixed(2) + 'px,'
                    + state.offsetY.toFixed(2) + 'px)';
            }
        }
        window.requestAnimationFrame(frame);
    }

    window.requestAnimationFrame(frame);
})();
