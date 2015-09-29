/* petros-sheet.js */

$(document).ready(function () {
    $("#contributor").focus();

    $(".clickable-row td:not(:first-child)").click(function() {
        window.document.location = $(this).parent().data("href");
    });

    $("#delete_entries").click(function() {
        var sheetId = $("#sheet-id").val();
                               
        var items = [];
        
        $(".item-select").each(function(ii, elem) {
            if ($(elem).prop("checked"))
                items.push(parseInt(elem.name.split("_")[1]));
        });

        $.ajax({
            type: "POST",
            url: "/sheet/" + sheetId + "/delete-items",
            contentType: 'application/json',
            data: JSON.stringify(items),
            success: function (res) {
                console.log("res", res);
                location.reload(true);
            }
        });
    });
});
