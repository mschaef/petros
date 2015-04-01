/* petros-sheet.js */

$(document).ready(function () {
    $("#amount").focus();

    $(".clickable-row").click(function() {
        window.document.location = $(this).data("href");
    });
});
