
${namespace} = new function() {
	var self = this;
	
  this.handleDownload = function(data) {
	  var form = document.createElement("form");
	  form.action=data;
	  form.method="POST";
	  document.body.appendChild(form);
	  form.submit();
  };

  this._execute = function(path, scriptName, methodName, args) {
    var la = args[args.length-1];
    var callback, errorHandler = self.defaultErrorHandler;
    if (typeof la == 'function') callback=la;
    else { callback=la[0]; errorHandler=la[1];  }
    var request = { callback: callback, path: path, scriptName: scriptName, methodName: methodName, args: [], errorHandler: errorHandler };   
    for (var i = 0; i < args.length-1; i++) request.args.push(self.serialize(request, args[i]));

    /*if (request.timeout) request.timeoutId = setTimeout(function() {
    		var t = request.transport; self.doRemove(request); if (t.abort) t.abort(request);
    	}, request.timeout);*/
    request.transport = request.fileUpload ? self.sendIframe : self.sendXhr;
    return request.transport(request);
  };

  this.defaultErrorHandler = function(ex,q) {
	  alert((ex.javaClassName ? ex.javaClassName : "" )+": "+ex.message);
	  console.log({error: ex, request: q});
  }
  
  this.handleCallback = function(cb,reply) {
    if (cb == console.log) console.log(reply); else if (cb) cb.apply(window, [ reply ]);
  };

  this.handleException = function(orig, reply) {
    orig.errorHandler.apply(window,[reply,orig]);
  };

  this.serialize = function(request, data) {
      if (data == null) { return "z:"; }
      switch (typeof data) {
      case "boolean": return "b:" + data;
      case "number": return "n:" + data;
      case "string": return "s:" + encodeURIComponent(data);
      case "object":
        var objstr = Object.prototype.toString.call(data);
        if (objstr == "[object String]") return "s:" + encodeURIComponent(data);
        else if (objstr == "[object Boolean]") return "b:" + data;
        else if (objstr == "[object Number]") return  "n:" + data;
        else if (objstr == "[object Date]") return "d:" + data.getTime();
        else if (objstr == "[object Array]") {
            var reply = "a:[";
            for (var i = 0; i < data.length; i++) {
              if (i != 0) reply += ",";
              reply += encodeURIComponent(self.serialize(request, data[i]));
            }
          return reply + "]"; }
        else if (data && data.tagName && data.tagName.toLowerCase() == "input" && data.type && data.type.toLowerCase() == "file") {
          request.fileUpload = true;
          return data;
        }
        else {
              var nft = false, reply = "o:{";
              for (var element in data) {
            	  if (nft) reply += ", "; else nft = true;
            	  reply += encodeURIComponent(element) + ":"+self.serialize(request, data[element]);
              }
              return reply + "}";
        }
        break;
      default:
        alert("serializing unknown data type: "+typeof data);
        return "u:" + data;
      }
    };

    this.sendXhr = function(q) {
        // Do proxies or IE force us to use early closing mode?
        q.req = new XMLHttpRequest();
        q.req.onreadystatechange = function() { self.xhrStateChange(q); };
        var request = self.constructRequest(q);
        q.req.open("POST", request.url, true);
        q.req.setRequestHeader("Content-Type", "text/plain");
        q.req.send(request.body);
      };

    this.xhrStateChange = function(orig) {
        var toEval;
        
        // Try to get the response HTTP status if applicable
        var req = orig.req;
        var status = req.readyState >= 2 ? req.status : 0;
        
        // If we couldn't get the status we bail out, unless the request is
        // complete, which means error (handled further below)
        if (status == 0 && req.readyState < 4) return;
        
        // The rest of this function only deals with request completion
        if (req.readyState != 4) return;

        try {
          var reply = req.responseText;
          if (status != 200 && status != 0) {
            self.handleException(orig, { name:"brijj.http." + status, message:req.statusText }); }
          else if (reply == null || reply == "") {
            self.handleException(orig, { name:"brijj.missingData", message:"No data received from server" }); }
          else {                     
            var contentType = req.getResponseHeader("Content-Type");
            toEval = reply; }
        }
        catch (ex) { self.handleException(orig, ex); }

        self.doResponse(orig,toEval);
        if (req) delete req;
      };

   this.doResponse = function(request, toEval) {
     if (toEval) {
     switch(toEval[0]) {
     case 'c': self.handleCallback(request.callback, eval(toEval.substring(2))); break;
     case 'x': self.handleException( request, eval(toEval.substring(2)) ); break;
     default: alert("unknown server-response type: "+toEval[0]);
     }
     }
   }
   
   this.sendIframe = function(request) {
        var div1 = document.createElement("div");
        document.body.appendChild(div1);
        div1.innerHTML = "<iframe src='about:blank' frameborder='0' name='xxxxx' style='width:0px;height:0px;border:0;display:none;'></iframe><form encType='multipart/form-data' encoding='multipart/form-data' method='POST' style='display:none'></form>";
        request.iframe = div1.firstChild;
        var iframe = request.iframe;
        var fr = function() { self.doResponse(request, request.iframe.contentDocument.body.innerHTML); document.body.removeChild(div1); delete div1; return true; };
        if (iframe.addEventListener) iframe.addEventListener("load", fr, true);
        if (iframe.attachEvent) iframe.attachEvent("onload", fr);

        var q = self.constructRequest(request);
        var form = div1.children[1];
        form.setAttribute("action", q.url);
        form.setAttribute("target", "xxxxx");
        for (var i=0;i<request.args.length;i++) {
          var value = request.args[i];
          var prop = "p"+i;
          if (value && value.tagName && value.tagName.toLowerCase() == "input" && value.type && value.type.toLowerCase() == "file") {
             var clone = value.cloneNode(true);
             value.removeAttribute("id");
             value.setAttribute("name", prop);
             value.style.display = "none";
             value.parentNode.insertBefore(clone, value);
             value.parentNode.removeChild(value);
             form.appendChild(value);
          } else {
             var formInput = document.createElement("input");
             formInput.setAttribute("type", "hidden");
             formInput.setAttribute("name", prop);
             formInput.setAttribute("value", value);
             form.appendChild(formInput);
          }
        }
        form.submit();
      };

  this.constructRequest = function(req) {
      var request = { url: req.path + "/call/" + req.scriptName + "." +req.methodName, body: "" }
      for (var i=0;i<req.args.length;i++) request.body += req.args[i] + "\n";
      return request;
    };
};
