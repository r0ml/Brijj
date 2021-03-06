
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
    var url = path + "/call/" + scriptName + "." +methodName;
    var body = "";
    try { for (var i=0;i< args.length;i++) body += self.serialize(args[i]) + "\n"; }
    catch(err) {
      if (err == "fileUpload") { return sendIframe(url, args /*, callback, errorHandler */ ); }
      else if (err == "formUpload") { body = args[0]; }
      else return alert(err);
    }

    var req = new XMLHttpRequest();
    req.callback = function(x) { console.log(x); };
    req.errback = self.defaultErrorHandler;

    req.open("POST", url, true);
    req.onreadystatechange = function() { self.xhrStateChange(req); };
    req.send(body);
    /* This generates a potential race condition:  the send happens before the callback function is set */
    return {
      then: function(callback, errback) {
        req.callback = callback;
        if (errback != null) req.errback = errback;
        return {
          except: function(errback) {
            req.errback = errback;
          }
        }
      }
    }
  };

  this.defaultErrorHandler = function(ex,q) {
	  alert((ex.javaClassName ? ex.javaClassName : "" )+": "+ex.message);
	  console.log({error: ex, request: q});
  }
  
  this.handleCallback = function(cb,reply) {
    if (cb == console.log) console.log(reply); else if (cb) cb.apply(window, [ reply ]);
  };

  this.handleException = function(errorHandler, reply) {
    errorHandler.apply(window,[reply]);
  };

  this.serialize = function(data) {
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
              reply += encodeURIComponent(self.serialize(data[i]));
            }
          return reply + "]"; }
        else if (objstr == "[object FormData]") {
          throw "formUpload";
        }
        else if (data && data.tagName && data.tagName.toLowerCase() == "input" && data.type && data.type.toLowerCase() == "file") {
          throw "fileUpload";
        }
        else {
              var nft = false, reply = "o:{";
              for (var element in data) {
            	  if (nft) reply += ", "; else nft = true;
            	  reply += encodeURIComponent(element + ":"+self.serialize(data[element]));
              }
              return reply + "}";
        }
        break;
      default:
        alert("serializing unknown data type: "+typeof data);
        return "u:" + data;
      }
    };

    this.xhrStateChange = function(req) {
        var toEval;
        
        // Try to get the response HTTP status if applicable
        var status = req.readyState >= 2 ? req.status : 0;
        
        // If we couldn't get the status we bail out, unless the request is
        // complete, which means error (handled further below)
        if (status == 0 && req.readyState < 4) return;
        
        // The rest of this function only deals with request completion
        if (req.readyState != 4) return;

        try {
          var reply = req.responseText;
          if (status != 200 && status != 0) {
            self.handleException(req.errback, { name:"brijj.http." + status, message:req.statusText }); }
          else if (reply == null || reply == "") {
            self.handleException(req.errback, { name:"brijj.missingData", message:"No data received from server" }); }
          else {                     
            var contentType = req.getResponseHeader("Content-Type");
            toEval = reply; }
        }
        catch (ex) { self.handleException(req.errback, ex); }

        self.doResponse(req.callback,req.errback,toEval);
        if (req) delete req;
      };

   this.doResponse = function(callback, errorHandler, toEval) {
     if (toEval) {
     switch(toEval[0]) {
     case 'c': self.handleCallback(callback, eval(toEval.substring(2))); break;
     case 'x': self.handleException( errorHandler, eval(toEval.substring(2)) ); break;
     default: alert("unknown server-response type: "+toEval[0]);
     }
     }
   }
   
   this.sendIframe = function(url, args, callback, errorHandler) {
        var div1 = document.createElement("div");
        document.body.appendChild(div1);
        div1.innerHTML = "<iframe src='about:blank' frameborder='0' name='xxxxx' style='width:0px;height:0px;border:0;display:none;'></iframe><form encType='multipart/form-data' encoding='multipart/form-data' method='POST' style='display:none'></form>";
        
        var iframe = div1.firstChild;
        iframe.callback = function(x) { console.log(x); }
        iframe.errback = self.defaultErrorHandler;
        var fr = function() { self.doResponse(iframe.callback, iframe.errback, iframe.contentDocument.body.innerHTML); document.body.removeChild(div1); delete div1; return true; };
        if (iframe.addEventListener) iframe.addEventListener("load", fr, true);
        if (iframe.attachEvent) iframe.attachEvent("onload", fr);
        
        var form = div1.children[1];
        form.setAttribute("action", url);
        form.setAttribute("target", "xxxxx");
        for (var i=0;i<args.length;i++) {
          var value = args[i];
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
        /* This generates a potential race condition, the send happens before the callback is set */
        return {
          then: function(callback, errback) {
            iframe.callback = callback;
            if (errback != null) iframe.errback = errback;
            return {
              except: function(errback) {
                iframe.errback = errback;
              }
            }
          }
        }
      };
};

{{functions}}
