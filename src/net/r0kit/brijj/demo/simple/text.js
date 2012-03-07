
function update() {
  var name = document.getElementById("demoName").value;
  Demo.sayHello(name, loadinfo);
}

function loadinfo(data) {
    document.getElementById("demoReply").innerText = data;
}
