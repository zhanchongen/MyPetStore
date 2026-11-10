/* 用户端交互：数量步进、商品图片切换和确认弹窗；不做任何金额计算。 */
(function () {
    "use strict";

    function clamp(value, min, max) {
        var number = parseInt(value, 10);
        if (isNaN(number)) {
            number = min;
        }
        return Math.min(max, Math.max(min, number));
    }

    document.addEventListener("click", function (event) {
        var minus = event.target.closest("[data-qty-minus]");
        var plus = event.target.closest("[data-qty-plus]");
        var thumb = event.target.closest("[data-image]");

        if (minus || plus) {
            var control = (minus || plus).closest("[data-qty-control]");
            if (!control) {
                return;
            }
            var input = control.querySelector("[data-qty-input]");
            if (!input) {
                return;
            }
            var min = parseInt(input.getAttribute("min") || "1", 10);
            var max = parseInt(input.getAttribute("max") || "99", 10);
            var step = minus ? -1 : 1;
            input.value = clamp(parseInt(input.value, 10) + step, min, max);
            input.dispatchEvent(new Event("change", { bubbles: true }));
            return;
        }

        if (thumb) {
            var gallery = thumb.closest("[data-gallery]");
            var main = gallery ? gallery.querySelector("[data-main-image]") : null;
            if (main) {
                main.src = thumb.getAttribute("data-image");
                thumb.parentElement.querySelectorAll("[data-image]").forEach(function (item) {
                    item.classList.toggle("is-active", item === thumb);
                });
            }
        }
    });

    document.addEventListener("change", function (event) {
        var input = event.target.closest("[data-qty-input]");
        if (!input) {
            return;
        }
        var min = parseInt(input.getAttribute("min") || "1", 10);
        var max = parseInt(input.getAttribute("max") || "99", 10);
        input.value = clamp(input.value, min, max);
    });

    document.querySelectorAll("form[data-confirm]").forEach(function (form) {
        form.addEventListener("submit", function (event) {
            if (!window.confirm(form.getAttribute("data-confirm"))) {
                event.preventDefault();
            }
        });
    });
})();
