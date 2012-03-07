
function xhr() { return new window.XMLHttpRequest(); }
function id(x) { return document.getElementById(x); }
function tags(x) { return document.getElementsByTagName(x);}
function clz(c) { return getElementsByClassName(c)[0]; }
function stop(e) { if (!e) var e = window.event; e.cancelBubble=true; if (e.stopPropagation) e.stopPropagation(); }

function qc(n,c) { if (n.className == undefined) return false; // this happens
																// in chrome
																// when n ==
																// document
	return n.className.match(new RegExp('(^| )'+c+'($| )')); }

function ac(n,c) { if (qc(n,c)) return; n.className+=' '+c; }
function rc(n,c) { n.className = n.className.replace(new RegExp('(^| )'+c+'($| )'),' '); }
function tc(n,c) { if (qc(n,c)) rc(n,c); else ac(n,c); }

function ne(t) { return document.createElement(t);}
function nd(c) { var r = ne("div"); r.className = c; return r; }



${namespace} = new function() {
	var self = this;
	self.formdatavariant = false;
	this.setTimeout = function(timeout) { self._timeout = timeout; };

  this.defaultErrorHandler = function(message, ex) {
    self._debug("Error: " + ex.name + ", " + ex.message, true);
    if (message == null || message == "") alert("A server error has occurred.");
    // Ignore NS_ERROR_NOT_AVAILABLE if Mozilla is being narky
    else if (message.indexOf("0x80040111") != -1) self._debug(message);
    else alert(message);
  };

  this.defaultWarningHandler = function(message, ex) { self._debug(message);  };

   
  /**
	 * For use with file downloads. When a brijj function returns a binary
	 * download you can prompt the user to save it using this function
	 * 
	 * @param {Object}
	 *            data The binary data passed from brijj
	 */
  this.openInDownload = function(data) {
/*    var div = document.createElement("div");
    document.body.appendChild(div);
    div.innerHTML = "<iframe width='0' height='0' scrolling='no' frameborder='0' src='" + data + "'></iframe>";
    */
	  var form = document.createElement("form");
	  form.action=data;
	  document.body.appendChild(form);
	  form.submit();
  };

  // ==============================================================================
  // Only private stuff below here
  // ==============================================================================

    this._pathTobrijjServlet = "${pathToBrijjServlet}";

  /** The webapp's context path (used for setting cookie path) */
  this._contextPath = "${contextPath}";

  /** These URLs can be configured from the server */
  this._ModePlainCall = "${plainCallHandlerUrl}";
  this._ModeHtmlCall = "${htmlCallHandlerUrl}";
  
  /** A map of the batches that we have sent and are awaiting a reply on. */
  this._batches = {};

  /** The global timeout */
  this._timeout = 0;

  /** Batch ids allow us to know which batch the server is answering */
  this._nextBatchId = 0;

  /** A list of the properties that need merging from calls to a batch */
  this._propnames = [ "timeout", "errorHandler", "warningHandler", "textHtmlHandler" ];
    
  /**
	 * A map of all mapped classes whose class declarations have been loaded
	 * (brijjClassName -> constructor function)
	 */
  this._mappedClasses = {};

  /** A function to call if something fails. */
  this._errorHandler = this.defaultErrorHandler;

  /** For debugging when something unexplained happens. */
  this._warningHandler = this.defaultWarningHandler;

  /** Object attributes to ignore when serializing */
  this._excludeObjectAttributes = {
    "$brijjClassName": true,
  };

  /**
	 * @private Abort any XHRs in progress at page unload (solves zombie socket
	 *          problems in IE).
	 */
  this._unloader = function() {
    // Abort any ongoing XHRs and clear their batches
    for (var bid = 0;bid<self._batches.length;bid++) {
      self._batches[bid].req.abort();
    }
  };

  /**
	 * Send a request. Called by the JavaScript interface stub
	 * 
	 * @private
	 * @param path
	 *            part of URL after the host and before the exec bit without
	 *            leading or trailing /s
	 * @param scriptName
	 *            The class to execute
	 * @param methodName
	 *            The method on said class to execute
	 * @param func
	 *            The callback function to which any returned data should be
	 *            passed if this is null, any returned data will be ignored
	 * @param args
	 *            The parameters to passed to the above method
	 */
  this._execute = function(path, scriptName, methodName, args) {
    var batch = {
        handlers:{},
        paramCount:0,
        timeout:self._timeout,
        errorHandler:self._errorHandler,
        warningHandler:self._warningHandler,
        path: path,
        headers: {}
    };   

    // From the other params, work out which is the function (or object with
    // call meta-data) and which is the call parameters
    var callData, stopAt;
    var lastArg = args[args.length - 1];
    if (lastArg == null || typeof lastArg == "function") {
    	callData = { callback:lastArg };
        stopAt = args.length - 1;
    }
    else if (typeof lastArg == "object" && (typeof lastArg.callback == "function"
        || typeof lastArg.exceptionHandler == "function" || typeof lastArg.callbackHandler == "function"
        || typeof lastArg.errorHandler == "function" || typeof lastArg.warningHandler == "function" )) {
        callData = lastArg;
        stopAt = args.length - 1;
    }
    else {
      callData = {};
      stopAt = args.length;
    }

      // Merge from the callData into the batch
    self.batch.merge(batch, callData);
    batch.handlers = {
        exceptionHandler:callData.exceptionHandler,
        exceptionArg:callData.exceptionArg || callData.arg || null,
        exceptionScope:callData.exceptionScope || callData.scope || window,
        callback:callData.callbackHandler || callData.callback,
        callbackArg:callData.callbackArg || callData.arg || null,      
        callbackScope:callData.callbackScope || callData.scope || window          
    };

      // Copy to the map the things that need serializing
    batch.map = {}
    batch.map[ "scriptName"] = scriptName;
    batch.map[ "methodName"] = methodName;
    for (var i = 0; i < stopAt; i++) {
        batch.map["param"+i] = self.serialize.convert(batch, args[i]);
    }
    return self.transport.send(batch);
  };

  /** @private This is a hack to make the context be this window */
  this._eval = function(script) {
    if (script == null) {
      return null;
    }
    if (script == "") {
      self._debug("Warning: blank script", true);
      return null;
    }
    // brijj.engine._debug("Exec: [" + script + "]", true);
    return eval(script);
  };

  /**
	 * Generic error handling routing to save having null checks everywhere
	 * 
	 * @private
	 * @param {Object}
	 *            batch
	 * @param {Object}
	 *            ex
	 */
  this._handleError = function(batch, ex) {
    // Perform error cleanup synchronously
    var errorHandlers = [];
    if (batch) {
        var handlers = batch.handlers;
        if (!handlers.completed) {
          if (typeof handlers.errorHandler == "function") errorHandlers.push(handlers.errorHandler);
          handlers.completed = true;
        }
    }
    if (batch) self.batch.remove(batch);

    self._prepareException(ex);
    var errorHandler;
    while(errorHandlers.length > 0) {
      errorHandler = errorHandlers.shift();
      errorHandler(ex.message, ex);
    }
    if (batch && typeof batch.errorHandler == "function") batch.errorHandler(ex.message, ex);
    else if (self._errorHandler) self._errorHandler(ex.message, ex);
  };

  // Generic error handling routing to save having null checks everywhere.
  this._handleWarning = function(batch, ex) {
    self._prepareException(ex); 
    if (batch && typeof batch.warningHandler == "function") batch.warningHandler(ex.message, ex);
    else if (self._warningHandler) self._warningHandler(ex.message, ex);
    if (batch) self.batch.remove(batch);
  };

  // Prepares an exception for an error/warning handler.
  this._prepareException = function(ex) {
    if (typeof ex == "string") ex = { name:"unknown", message:ex };
    if (ex.message == null) ex.message = "";
    if (ex.name == null) ex.name = "unknown";
  };
  
  // Create a new object that delegates to obj
  this._createFromMap = function(map) {
    var obj = new self(); // this should be set to a constructor function!
    for(prop in map) if (map.hasOwnProperty(prop)) obj[prop] = map[prop];
    return obj;
  };
  
  // A reference to the global context (window when in a browser)
  this._global = (function(){return this;}).call(null);
  
  /**
	 * Use this to be able to use client side scopes for brijj objects
	 */
  this._getObject = function(prop) {
    var parts = prop.split(".");
    var value;
    var scope = self._global;
    while(parts.length > 0) {
      var currprop = parts.shift();
      value = scope[currprop];
      if (parts.length > 0 && value == null) return undefined;
      scope = value;
    }
    return value;
  };

  /**
	 * Navigates properties from the global scope and down to set a value.
	 * 
	 * @param prop
	 *            hierarchical property name
	 * @param obj
	 *            property value to set
	 */
  this._setObject = function(prop, obj) {
    var parts = prop.split(".");
    var level;
    var scope = self._global;
    while(parts.length > 0) {
      var currprop = parts.shift();
      if (parts.length == 0) {
        scope[currprop] = obj;
      }
      else {
        level = scope[currprop];
        if (level == null) {
          scope[currprop] = level = {};
        }
        scope = level;
      }
    }
  };
  
  /**
	 * Used internally when some message needs to get to the programmer
	 * 
	 * @private
	 * @param {String}
	 *            message
	 * @param {Object}
	 *            stacktrace
	 */
  this._debug = function(message, stacktrace) {
    if (stacktrace && window.console.trace) window.console.trace();
    window.console.log(message);
  };

  /**
	 * Functions called by the server
	 */
  this.remote = {
    handleDownload:function(batchId, src) {
      var batch = self._batches[batchId];
      var form = document.createElement("form");
      form.action=src;
      form.method="GET";
      document.body.appendChild(form);
      form.submit();
      batch.completed=true;
    },
    
    /**
	 * Execute a callback
	 * form
	 * @private
	 * @param {int}
	 *            batchId The ID of the batch that we are replying to
	 * @param {String}
	 *            reply The script to execute
	 */
    handleCallback:function(batchId, reply) {
      var batch = self._batches[batchId];
      if (batch == null) {
        self._debug("Warning: batch == null in remoteHandleCallback for batchId=" + batchId, true);
        return;
      }

      // We store the reply in the batch so that in sync mode we can return
		// the data
      batch.reply = reply;

      // Error handlers inside here indicate an error that is nothing to do
      // with brijj so we handle them differently.
      try {
        var handlers = batch.handlers;
        if (!handlers) {
          self._debug("Warning: Missing handlers.", true);
        }
        else {
          batch.handlers.completed = true;
          if (typeof handlers.callback == "function") {
            handlers.callback.apply(handlers.callbackScope, [ reply, handlers.callbackArg ]);
          }
        }
      }
      catch (ex) {
        self._handleError(batch, ex);
      }
    },

    /**
	 * Called by the server: Handle an exception for a call
	 * 
	 * @private
	 * @param {int}
	 *            batchId The ID of the batch that we are replying to
	 * @param {String}
	 *            reply The script to execute
	 */
    handleException:function(batchId, ex) {
      var batch = self._batches[batchId];
      if (batch == null) {
        self._debug("Warning: null batch in remoteHandleException", true);
        return;
      }

      var handlers = batch.handlers;
      batch.handlers.completed = true;
      if (handlers == null) {
        self._debug("Warning: null handlers in remoteHandleException", true);
        return;
      }

      if (ex.message == undefined) {
        ex.message = ""; 
      }

      if (typeof handlers.exceptionHandler == "function") {
        handlers.exceptionHandler.call(handlers.exceptionScope, ex.message, ex, handlers.exceptionArg);
      }
      else if (typeof batch.errorHandler == "function") {
        batch.errorHandler(ex.message, ex);
      }
    },

    /**
	 * Called by the server: Create a new object of a mapped class
	 * 
	 * @private
	 * @param {string}
	 *            brijjClassName the name of the mapped class
	 * @param {Object}
	 *            memberMap the object's data members
	 */
    newObject:function(brijjClassName, memberMap){
      var classfunc = self._mappedClasses[brijjClassName]; 
      if (classfunc && classfunc.createFromMap) {
        return classfunc.createFromMap(memberMap);
      }
      else {
        memberMap.$brijjClassName = brijjClassName;
        return memberMap;
      }
    }
  };

  // Functions to serialize a data set into a list of parameters
  this.serialize = {
     // Marshall a data item
    convert:function(batch, data) {
      if (data == null) { return "null:null"; }

      switch (typeof data) {
      case "boolean": return "boolean:" + data;
      case "number": return "number:" + data;
      case "string": return "string:" + encodeURIComponent(data);
      case "object":
        var objstr = Object.prototype.toString.call(data);
        if (objstr == "[object String]") return "string:" + encodeURIComponent(data);
        else if (objstr == "[object Boolean]") return "boolean:" + data;
        else if (objstr == "[object Number]") return  "number:" + data;
        else if (objstr == "[object Date]") return "date:" + data.getTime();
        else if (objstr == "[object Array]") return self.serialize.convertArray(batch, data);
        else if (data && data.tagName && data.tagName.toLowerCase() == "input" && data.type && data.type.toLowerCase() == "file") {
          batch.fileUpload = true;
          return data;
        }
        else {
          if (data.nodeName && data.nodeType) return self.serialize.convertXml(data);
          else return self.serialize.convertObject(batch, data);
        }
        break;
      default:
        self._handleWarning(null, { name:"brijj.engine.unexpectedType", message:"Unexpected type: " + typeof data + ", attempting default converter." });
        return "default:" + data;
      }
    },

    convertArray:function(batch, data) {
        // Use string concat on other browsers (fastest)
        var reply = "array:[";
        for (var i = 0; i < data.length; i++) {
          if (i != 0) reply += ",";
          reply += encodeURIComponent(self.serialize.convert(batch, data[i]));
        }
        reply += "]";
      return reply;
    },

    convertObject:function(batch, data) {
      // treat objects as an associative arrays
      var reply = "Object_" + self.serialize.getObjectClassName(data).replace(/:/g, "?") + ":{";
      var elementset = (data.constructor && data.constructor.$brijjClassMembers ? data.constructor.$brijjClassMembers : data);
      var element;
      for (element in elementset) {
        if (typeof data[element] != "function" && !self._excludeObjectAttributes[element]) {
           reply += encodeURIComponent(element) + ":"+
           		self.serialize.convert(batch, data[element]) + ", ";
        }
      }

      if (reply.substring(reply.length - 2) == ", ") {
        reply = reply.substring(0, reply.length - 2);
      }
      reply += "}";
      return reply;
    },

    convertXml:function(data) {
      var output;
      if (window.XMLSerializer) output = new XMLSerializer().serializeToString(data);
      else if (data.toXml) output = data.toXml;
      else output = data.innerHTML;
      return "xml:" + encodeURIComponent(output);
    },
    
    /**
	 * Returns the classname of supplied argument obj. Similar to typeof, but
	 * which returns the name of the constructor that created the object rather
	 * than 'object'
	 * 
	 * @private
	 * @param {Object}
	 *            obj The object to detect the type of
	 * @return The name of the object
	 */
    getObjectClassName:function(obj) {
      // Different handling depending on if, and what type of, class-mapping
		// is used
      if (obj.$brijjClassName)
        return obj.$brijjClassName; // Light class-mapping uses the classname
									// from a property on the instance
      else if (obj.constructor && obj.constructor.$brijjClassName)
        return obj.constructor.$brijjClassName; // Full class-mapping uses the
												// classname from a property on
												// the constructor function
      else
        return "Object";
    }
  };

  // Functions to handle the various remoting transport
  this.transport = {
    send:function(batch) {
      self.batch.prepareToSend(batch);
      if (batch.path == null) {
        batch.path = self._pathTobrijjServlet;
      }    
      var t = self.transport;
      batch.transport = batch.fileUpload ? t.iframe : t.xhr;
      return batch.transport.send(batch);
    },

    // Called to signal that the batch response has been delivered
    complete:function(batch) {
        self.batch.validate(batch);
        self.transport.remove(batch);
    },
    
    // Called as a result of a request timeout
    abort:function(batch) {
      var transport = batch.transport;
      self.transport.remove(batch);
      if (transport.abort) {
        transport.abort(batch);
      }
      self._handleError(batch, { name:"brijj.engine.timeout", message:"Timeout" });
    },

    // Remove all remoting artifacts
    remove:function(batch) {
      if (batch.transport) {
        batch.transport.remove(batch);
        batch.transport = null;
      }
      self.batch.remove(batch);
    },

    // Remoting through XHR
    xhr:{
      httpMethod:"POST",
      
      send:function(batch) {
        // Do proxies or IE force us to use early closing mode?
        batch.req = new XMLHttpRequest();
        batch.req.onreadystatechange = function() { self.transport.xhr.stateChange(batch); };

        httpMethod = self.transport.xhr.httpMethod;
        batch.mode = self._ModePlainCall;
        var request = self.batch.constructRequest(batch, httpMethod);

        try {
          batch.req.open(httpMethod, request.url, true);
          try {
            for (var prop in batch.headers) {
              var value = batch.headers[prop];
              if (typeof value == "string") { batch.req.setRequestHeader(prop, value); }
            }
            
            if (!self.formdatavariant) {
            if (!batch.headers["Content-Type"]) {
              	batch.req.setRequestHeader("Content-Type", "text/plain");
              }
            }
          }
          catch (ex) { self._handleWarning(batch, ex); }
          batch.req.send(request.body);
        }
        catch (ex) { self._handleError(batch, ex); }
        return batch.reply;        // This is only of any use in sync mode to return the reply data
      },

      stateChange:function(batch) {
        var toEval;

        if (batch.completed) { self._debug("Error: _stateChange() with batch.completed"); return; }

        // Try to get the response HTTP status if applicable
        var req = batch.req;
        var status = 0;
        try { if (req.readyState >= 2) { status = req.status; } } // the try/catch is for mozilla? 
        catch(ignore) {}

        // If we couldn't get the status we bail out, unless the request is
        // complete, which means error (handled further below)
        if (status == 0 && req.readyState < 4) { return; }

        // The rest of this function only deals with request completion
        if (req.readyState != 4) { return; }

        try {
          var reply = req.responseText;
          if (status != 200 && status != 0) {
            self._handleError(batch, { name:"brijj.engine.http." + status, message:req.statusText });
          }
          else if (reply == null || reply == "") {
            self._handleError(batch, { name:"brijj.engine.missingData", message:"No data received from server" });
          }
          else {                     
            var contentType = req.getResponseHeader("Content-Type");
            if (!contentType.match(/^text\/plain/) && !contentType.match(/^text\/javascript/)) {
              if (contentType.match(/^text\/html/) && typeof batch.textHtmlHandler == "function") {
                batch.textHtmlHandler({ status:status, responseText:reply, contentType:contentType });
              }
              else {
                self._handleWarning(batch, { name:"brijj.engine.invalidMimeType", message:"Invalid content type: '" + contentType + "'" });
              }
            }
            else {
                 if (reply.search("//#BRIJJ") == -1) {
                  self._handleWarning(batch, { name:"brijj.engine.invalidReply", message:"Invalid reply from server" });
                }
                else {
                  toEval = reply;
                }
            }
          }
        }
        catch (ex) { self._handleWarning(batch, ex); }

        // Outside of the try/catch so errors propagate normally:
        self._receivedBatch = batch;
        self._eval(toEval);
        self._receivedBatch = null;
        self.transport.complete(batch);
      },

      abort:function(batch) { if (batch.req) { batch.req.abort(); } },
      remove:function(batch) { if (batch.req) { delete batch.req; } }
    },


    /**
	 * Functions for remoting through IFrame
	 */
    iframe:{
      httpMethod:"POST",

      /**
		 * Setup a batch for transfer through IFrame
		 * 
		 * @param {Object}
		 *            batch The batch to alter for IFrame transmit
		 */
      send:function(batch) {
        if (document.body == null) {
          setTimeout(function(){self.transport.iframe.send(batch);}, 100);
          return;
        }
        batch.httpMethod = self.transport.iframe.httpMethod;
        if (batch.fileUpload) {
          batch.httpMethod = "POST";
          batch.encType = "multipart/form-data";
        }
        var idname = self.transport.iframe.getId(batch);
        batch.div1 = document.createElement("div");
        document.body.appendChild(batch.div1);
        batch.div1.innerHTML = "<iframe src='about:blank' frameborder='0' style='width:0px;height:0px;border:0;display:none;' id='" + idname + "' name='" + idname + "'></iframe>";
        batch.iframe = batch.div1.firstChild;
        batch.document = document;
        batch.iframe.batch = batch;
        self.transport.iframe.beginLoader(batch, idname);
      },

      /**
		 * Create a unique ID so multiple iframes can fire at the same time
		 * 
		 * @param {Object}
		 *            batch A source of a unique number for the batch
		 * @return {String} a name prefix for created elements
		 */
      getId:function(batch) {
        return "brijj-if-" + batch.batchId;
      },

      /**
		 * Setup a form or construct a src attribute to use the iframe. This is
		 * abstracted from send() because the same logic will do for htmlfile
		 * 
		 * @param {Object}
		 *            batch
		 */
      beginLoader:function(batch, idname) {
        if (batch.iframe.contentWindow.document.body == null) {
          setTimeout(function(){self.transport.iframe.beginLoader(batch, idname);}, 100);
          return;
        }
        batch.mode = self._ModeHtmlCall;
        var request = self.batch.constructRequest(batch, batch.httpMethod);

          // TODO: On firefox we can now get the values of file fields, maybe
			// we should use this
          // See
			// http://soakedandsoaped.com/articles/read/firefox-3-native-ajax-file-upload
          // setting enctype via the DOM does not work in IE, create the form
			// using innerHTML instead
          batch.div2 = document.createElement("div");
          document.body.appendChild(batch.div2);
          batch.div2.innerHTML = "<form" + (batch.encType ? " encType='" + batch.encType + "' encoding='" + batch.encType + "'" : "") + "></form>";
          batch.form = batch.div2.firstChild;
          batch.form.setAttribute("action", request.url);
          batch.form.setAttribute("target", idname);
          batch.form.setAttribute("style", "display:none");
          batch.form.setAttribute("method", batch.httpMethod);
          for (var prop in batch.map) {
            var value = batch.map[prop];
            if (typeof value != "function") {
              if (value && value.tagName && value.tagName.toLowerCase() == "input" && value.type && value.type.toLowerCase() == "file") {
                // Since we can not set the value of a file object, we must post
				// the actual file object
                // that the user clicked browse on. We will put a clone in it's
				// place.
                var clone = value.cloneNode(true);
                value.removeAttribute("id", prop);
                value.setAttribute("name", prop);
                value.style.display = "none";
                value.parentNode.insertBefore(clone, value);
                value.parentNode.removeChild(value);
                batch.form.appendChild(value);
              } else {
                var formInput = batch.document.createElement("input");
                formInput.setAttribute("type", "hidden");
                formInput.setAttribute("name", prop);
                formInput.setAttribute("value", value);
                batch.form.appendChild(formInput);
              }
            }
           batch.form.submit();
        }
      },

      /**
		 * Functions designed to be called by the server
		 */
      remote:{
        /**
		 * Called by the server: An IFrame reply is about to start
		 * 
		 * @private
		 * @param {Object}
		 *            iframe
		 * @param {int}
		 *            batchId
		 */
        beginIFrameResponse:function(iframe, batchId) {
          if (iframe != null) self._receivedBatch = iframe.batch;
        },

        /**
		 * Called by the server: An IFrame reply is just completing
		 * 
		 * @private
		 * @param {int}
		 *            batchId
		 */
        endIFrameResponse:function(batchId) {
          self._receivedBatch = self._batches[batchId];
          self.transport.complete(self._receivedBatch);
          self._receivedBatch = null;
        }
      },

      remove:function(batch) {
        // Safari 3 and Chrome 1 will show endless loading spinner if removing
        // iframe during execution of iframe script, so we delay it a bit
        setTimeout(function(){
          if (batch.iframe && batch.iframe.parentNode) {
            batch.iframe.parentNode.removeChild(batch.iframe);
            batch.iframe = null;
          }
          if (batch.div1 && batch.div1.parentNode) {
            batch.div1.parentNode.removeChild(batch.div1);
            batch.div1 = null;
          }
          if (batch.form && batch.form.parentNode) {
            batch.form.parentNode.removeChild(batch.form);
            batch.form = null;
          }
          if (batch.div2 && batch.div2.parentNode) {
            batch.div2.parentNode.removeChild(batch.div2);
            batch.div2 = null;
          }
        }, 100);
      }
    },
  };
  
  // Functions to manipulate batches
  this.batch = {

    /**
	 * Take further options and merge them into a batch
	 * 
	 * @private
	 * @param {Object}
	 *            batch The batch that we are altering
	 * @param {Object}
	 *            overrides The object containing properties to copy into batch
	 */
    merge:function(batch, overrides) {
      var propname, data;
      for (var i = 0; i < self._propnames.length; i++) {
        propname = self._propnames[i];
        if (overrides[propname] != null) batch[propname] = overrides[propname];
      }
      if (overrides.headers) {
        for (propname in overrides.headers) {
          data = overrides.headers[propname];
          if (typeof data != "function") batch.headers[propname] = data;
        }
      }
    },

    // Executed just before a transport sends the batch
    prepareToSend:function(batch) {
      batch.batchId = self._nextBatchId;
      batch.map["batchId"]=batch.batchId;
      self._nextBatchId++;
      self._batches[batch.batchId] = batch;
      batch.completed = false;

      // Set a timeout
      if (batch.timeout && batch.timeout != 0) {
        batch.timeoutId = setTimeout(function() { self.transport.abort(batch); }, batch.timeout);
      }
    },

    /**
	 * Work out what the URL should look like
	 * 
	 * @private
	 * @param {Object}
	 *            batch the data that we are sending
	 * @param {Object}
	 *            httpMethod Are we using GET/POST etc?
	 */
    constructRequest:function(batch, httpMethod) {
      // A quick string to help people that use web log analysers
      var urlBuffer = [];
      urlBuffer.push(batch.path);
      urlBuffer.push(batch.mode);
        urlBuffer.push(batch.map["scriptName"]);
        urlBuffer.push(".");
        urlBuffer.push(batch.map["methodName"]);
        urlBuffer.push(".brijj");

      var request = {};
      var prop;
    	  
    	  if (self.formdatavariant) {
    	   	  request.body = new FormData();
        	  for (prop in batch.map) {
        		  if (typeof batch.map[prop] != "function") {
        			request.body.append(prop, batch.map[prop]);  
        		  }
        	  }

    	  }
    	  else {
    	request.body = "";
          // Use string concat on other browsers (fastest)
          for (prop in batch.map) {
            if (typeof batch.map[prop] != "function") {
              request.body += prop + "=" + batch.map[prop] + "\n";
            }
          }
    	  }
      request.url = urlBuffer.join("");
      return request;
    },

    /**
	 * @private This function is invoked when a batch reply is received. It
	 *          checks that there is a response for every call in the batch.
	 *          Otherwise, an error will be signaled (a call without a response
	 *          indicates that the server failed to send complete batch
	 *          response).
	 */
    validate:function(batch) {
      // If some call left unreplied, report an error.
      if (!batch.completed) {
          if (batch.handlers.completed !== true) {
            self._handleError(batch, { name:"brijj.engine.incompleteReply", message:"Incomplete reply from server" });
          }
      }
    },

    /**
	 * A call has finished by whatever means and we need to shut it all down.
	 * 
	 * @private
	 * @param {Object}
	 *            batch The batch that we are altering
	 */
    remove:function(batch) {
      if (!batch) {
        self._debug("Warning: null batch in brijj.engine.batch.remove()", true);
        return;
      }

      if (batch.completed) {
        return;
      }
      batch.completed = true;

      // Transport tidyup
      self.transport.remove(batch);

      // Timeout tidyup
      if (batch.timeoutId != null) {
        clearTimeout(batch.timeoutId);
        delete batch.timeoutId;
      }

      // TODO: co-locate all the functions that work on a set of batches
      if (batch.batchId || batch.batchId == 0) {
        delete self._batches[batch.batchId];
      }

    }
  };

  // Transform a number into a token string suitable for ids
  this.tokenify = function(number) {
    var tokenbuf = [];
    var charmap = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ*$";
    var remainder = number;
    while (remainder > 0) {
      tokenbuf.push(charmap.charAt(remainder & 0x3F));
      remainder = Math.floor(remainder / 64); }
    return tokenbuf.join("");
  };
    
  this.addEventListener = function(elem, name, func) {
    if (elem.addEventListener) elem.addEventListener(name, func, false);
    else elem.attachEvent("on" + name, func);
  };

  // Register the unload handler
  this.addEventListener(window, 'unload', this._unloader);

  // Set up a receiver context for this engine instance
  var g = this._global;
  if (!g.brijj) {
    g.brijj = {};
  }

  g.brijj._ = {
    handleDownload: this.remote.handleDownload,
    handleCallback: this.remote.handleCallback,
    handleException: this.remote.handleException,
    newObject: this.remote.newObject,
    beginIFrameResponse: this.transport.iframe.remote.beginIFrameResponse,
    endIFrameResponse: this.transport.iframe.remote.endIFrameResponse,
    _eval: this._eval
  };

  // Run page init code as desired by server
  // eval("${initCode}");
};
