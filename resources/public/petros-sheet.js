/* petros-sheet.js */

$(document).ready(function () {
    $("#contributor").focus();

    $(".clickable-row").click(function() {
        window.document.location = $(this).data("href");
    });
});
