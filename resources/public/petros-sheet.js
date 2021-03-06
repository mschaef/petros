/* petros-sheet.js */

$(document).ready(function () {
    function setFocus() {
        var errors = $("input.error");

        if (errors.length > 0) {
            errors[0].focus();
        } else {
            $("#contributor").focus();
        }
    }
    

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
                location.reload(true);
            }
        });
    });

    function updateUI() {
        var selected = false;
        
        $(".item-select").each(function(ii, elem) {
            if ($(elem).prop("checked"))
                selected = true;
        });

        $("#delete_entries").prop('disabled', !selected);        
    }
    
    $(".item-select").click(function () {
        updateUI();

        $(this).parent().parent().toggleClass("selected", $(this).prop("checked"));
    });
    
    $("#finalize_sheet").click(function() {
        if (!confirm("Finalize sheet? This cannot be reversed."))
            return;

        var sheetId = $("#sheet-id").val();
        
        $.ajax({
            type: "POST",
            url: "/sheet/" + sheetId + "/finalize",
            success: function (res) {
                location.reload(true);
            }
        });
    });

    $(document).scroll(function() {
        var scrollTop = $(this).scrollTop();

        $("#toolbar").toggleClass("shadowed", scrollTop > 0);
    });
       
    updateUI();
    setFocus();
});
