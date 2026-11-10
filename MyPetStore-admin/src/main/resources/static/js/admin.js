(function () {
    "use strict";

    document.querySelectorAll("form[data-confirm]").forEach(function (form) {
        form.addEventListener("submit", function (event) {
            var message = form.getAttribute("data-confirm") || "确认执行该操作？";
            if (!window.confirm(message)) {
                event.preventDefault();
            }
        });
    });
})();
