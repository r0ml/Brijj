function init() {
  Tabs('tabList');
  fillTable();
}

var peopleCache = { };
var viewed = -1;

function fillTable() {
  People.getSmallCrowd(function(people) {
    // Delete all the rows except for the "pattern" row
	  var z = document.querySelector("#peoplebody>#pattern").outerHTML;
	  var pb = document.getElementById("peoplebody");
	  pb.innerHTML=z;

    // Create a new set cloned from the pattern row
    var person, id;
    people.sort(function(p1, p2) { return p1.name.localeCompare(p2.name); });
    var pt = document.getElementById("pattern");

    for (var i = 0; i < people.length; i++) {
      person = people[i];
      id = person.id;
      
      var nx = pt.cloneNode(true);
      nx.setAttribute("id", "pattern"+id);
      var chd = nx.querySelectorAll("[id]");
      for( var k = 0; k < chd.length; k++ ) {
    	 chd[k].setAttribute("id", chd[k].getAttribute("id")+id );
      }
      pb.appendChild(nx);
      document.getElementById("tableName" + id).innerText = person.name;
      document.getElementById("tableAge" + id).innerText = person.age;
      document.getElementById("tableAddress" + id).innerText = person.address;
      document.getElementById("tableSuperhero" + id).innerText = person.superhero ? "Yes" : "No";
      nx.style.display = ""; // officially we should use table-row, but IE prefers "" for some reason
      peopleCache[id] = person;
    }
  });
}

function editClicked(eleid) {
  // we were an id of the form "edit{id}", eg "edit42". We lookup the "42"
  var person = peopleCache[eleid.substring(4)];  
  for(var z in person) {
	var e = document.getElementById(z);
	if (e != null) {
		if (e.type == "checkbox") e.checked = person[z];
		else e.value = person[z];
	}
  }
}

function deleteClicked(eleid) {
  // we were an id of the form "delete{id}", eg "delete42". We lookup the "42"
  var person = peopleCache[eleid.substring(6)];
  if (confirm("Are you sure you want to delete " + person.name + "?")) {

	  // FIXME: the API should do the right thing, or use continuations
//    brijj.engine.beginBatch();
    People.deletePerson(person.id);
    fillTable();
//    brijj.engine.endBatch();
  }
}

function writePerson() {
  var person = {
     id: document.getElementById("id").value,
     name: document.getElementById("name").value,
     address: document.getElementById("address").value,
     age: document.getElementById("age").value,
     superhero: document.getElementById("superhero").checked
  }
  
//  brijj.engine.beginBatch();
  People.setPerson(person);
  fillTable();
//  brijj.engine.endBatch();
}

function clearPerson() {
  viewed = -1;
  var clr = { id:-1, name:null, address:null, salary:null, age: null };
  for(var z in clr) {
	var e = document.getElementById(z);
	if (e != null) e.value = clr[z];
  }  
}