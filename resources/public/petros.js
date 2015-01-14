/* petros.js */

sidebarVisible = false;


function toggleSidebar()
{
    $('#overlay').animate({left: (sidebarVisible ? "-270px" : "0px") });
    $('#header').animate({left: (sidebarVisible ? "0px" : "270px") });

    $('.wrapper').animate({"left": (sidebarVisible ? "0px" : "270px") });


    sidebarVisible = !sidebarVisible;
}

function beginListCreate()
{
  var formMarkup = "";

    formMarkup += "<td colspan=\"2\"><form action=\"/list\" method=\"POST\">";
    formMarkup += "<input class=\"full-width simple-border\" id=\"list-description\" name=\"list-description\" type=\"text\" maxlength=\"32\"/>";
    formMarkup += "</form></td>";

    $('td.add-list').replaceWith(formMarkup);

    $("#list-description").focus();
}

function beginUserAdd(listId)
{
  var formMarkup = "";

    formMarkup += "<input class=\"full-width simple-border\" id=\"share-with-email\" name=\"share-with-email\" type=\"text\" />";

    $('p.new-user').replaceWith(formMarkup);

    $("#share-with-email").focus();
}
