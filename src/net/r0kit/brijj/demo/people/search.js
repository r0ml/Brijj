
var peopleCache = [ ];
var lastFilter = "";

function init() {
	  Tabs('tabList');
	  document.getElementById("filter").value = "";
	  document.getElementById("peoplebody").innerHTML = "<tr><td colspan=3>Please enter a search filter</td></tr>";
	}

function fillTable(people) {
  var filter = document.getElementById("filter").value;
  var pattern = new RegExp("(" + filter + ")", "i");
  var pb = document.getElementById("peoplebody");
  pb.innerHTML="";
  for (i = 0; i < people.length; i++) {
    var tp = people[i];
    if (pattern.test(tp.name)) {
    	var c = pb.appendChild(document.createElement("tr"));
    	c.innerHTML="<td>"+tp.name.replace(pattern,"<span class='highlight'>$1</span>")
    	  +"</td><td>"+tp.age+"</td><td>"+tp.address+"</td>";
    }
  }
  if (pb.childElementCount == 0) {
	  pb.innerHTML="<tr><td colspan=3>No matches</td></tr>";
  }
  peopleCache = people;
}

function filterChanged() {
  var filter = document.getElementById("filter").value;
  if (filter.length == 0) {
    document.getElementById("peoplebody").innerHTML="<tr><td colspan=3>Please enter a search filter</td></tr>";
  }
  else {
    if (filter.charAt(0) == lastFilter.charAt(0)) {
      fillTable(peopleCache);
    }
    else {
      People.getMatchingFromLargeCrowd(filter.charAt(0), fillTable);
    }
  }
  lastFilter = filter;
}
