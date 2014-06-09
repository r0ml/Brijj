
/* To get a handle for debugging from the console, use:
 * h = angular.element('html'); // or whatever element has a scope
 * swb = h.injector().get('SWBrijj')
 * 
 * Debugging looks something like this:
 * 
 * h.scope().$apply( function() { swb.kvt('config.features').then(function(x) { console.log(x); } ) } )
 * 
 */

function xadb(hz, a) { var h = angular.element(hz); var b = Array.prototype.slice.call(arguments,2); var swb = h.injector().get('{{scriptName}}'); h.scope().$apply( function() { swb[a].apply(swb, b).then(function(x) { console.log(x); } ) } ) };

var _bm = angular.module('brijj',[], function($httpProvider) {
    $httpProvider.responseInterceptors.push('errorHttpInterceptor');  
});

_bm.factory('errorService', function() {
    return {
        errorMessage: null, setError: function(msg) {
            this.errorMessage = msg; },
    clear: function() { this.errorMessage = null;
    } };
});

_bm.factory('errorHttpInterceptor',
        function ($q, $location, $rootScope, errorService) { return function (promise) {
            return promise.then(function (response) {
                if (response.data[0]=='x') {
                    /** @type { {javaClassName: string, message: string } }*/
                    var errm = eval(response.data.substr(2));
                    if (errm.javaClassName == "net.r0kit.brijj.BrijjServlet$NotLoggedIn") {
                        $rootScope.$broadcast('event:loginRequired');
                        return response;
                    }
                    $rootScope.$broadcast('event:brijjError', errm.message);
                    return response;
                } else
                return response;
            }, function (response) {
                if (response.status === 401) {
                    $rootScope.$broadcast('event:loginRequired');
                } else if (response.status >= 400 && response.status < 500) {
                    errorService.setError('Server was unable to find' +
                        ' what you were looking for... Sorry!!');
                }
                return $q.reject(response); });
        }; });

_bm.factory('logService', function($rootScope) {
    return {
        log: [],
        sendLog: function(log) {
            $rootScope.$broadcast('dblog:updated', this.log);
        },
        addItem: function(log_item, startTime) {
            log_item.time = Date.now()-startTime;
            this.log.push(log_item);
            this.sendLog();
            // could restrict this to only send under certain conditions
        },
        clearLog: function() {
            this.log = [];
        }
    };
});
/* */
/* bbbb = function(res) { angular.module('aaa',[]).service('bbb', function($q,$http) { ccc.$http = $http; ccc.$q = $q; } ); return ccc; } */
/* angular.module('main',['aaa']).controller('zzz',function(bbb) {}); */
_bm.factory('{{scriptName}}', ['$http','$q','$injector', '$rootScope', 'logService', function($http,$q,$injector,$rootScope, logService) {
    return {
        _path: '/brijj/',
    defaultErrorHandler:  // function(x) {alert(x.javaClassName+": "+x.message); },
    function(errm) {
        $rootScope.$broadcast( errm.javaClassName == 'net.r0kit.brijj.BrijjServlet$NotLoggedIn' ?
            'event:loginRequired' : 'event:brijjError', errm.message);
    },

    _execute: function(scriptName, methodName, args) {
        var url = this._path + "/call/" + scriptName + "." +methodName;
        var body = "";
        var log_item = {"page": document.location.href, "method": methodName, "args": JSON.stringify(args)};
        var start = Date.now();
        try { for (var i=0;i< args.length;i++) body += this._serialize(args[i]) + "\n"; }
        catch(err) {
            if (err == "fileUpload") { return sendIframe(url, args, callback, errorHandler); }
            else if (err == "formUpload") { 
                body = args[0];
                var req = new XMLHttpRequest();
                req.callback = function(x) { console.log(x); };
                req.errback = this.defaultErrorHandler;

                req.open("POST", url, true);
                req.xhrStateChange = this.xhrStateChange;
                req.doResponse = this.doResponse;
                req.handleException = this.handleException;
                req.handleCallback = this.handleCallback;
                req.onreadystatechange = function() {
                    req.xhrStateChange(req); };
                var res = {
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
                res.xhr = req;

                var  $rootScope = $injector.get("$rootScope");
                req.upload.addEventListener("progress", function(evt) {$rootScope.$broadcast("upload:progress", evt);}, false);
                req.upload.addEventListener("load", function(evt) {$rootScope.$broadcast("upload:load", evt);}, false);
                req.upload.addEventListener("error", function(evt) {$rootScope.$broadcast("upload:error", evt);}, false);
                req.upload.addEventListener("abort", function(evt) {$rootScope.$broadcast("upload:abort", evt);}, false);
                req.send(body);
                //var promise = $q.defer().promise;
                //           return promise;
                return res;
            }
            else return alert(err);
        }

        var req = $http.post(url, body);
        req.brijj = this;
        /*       return {
                 then: function(x) { req.success( { }); }
                 }
                 req.then = req.success;
                 req.except = req.error;
                 */     
        // req.callback = function(x) { console.log(x); };
        // req.errback = self.defaultErrorHandler;

        /*  if (req.status == 200 || req.status == 0) {
            switch(req.data[0]) {
            case 'c': self.handleCallback(callback, eval(toEval.substring(2))); break;
            case 'x': self.handleException( errorHandler, eval(toEval.substring(2)) ); break;
            default: alert("unknown server-response type: "+toEval[0]);
            }
            }
            */

        return {
            then: function(x) {
                var zfn = function(z) {
                    logService.addItem(log_item, start);
                    switch( z.data[0]) {
                        case 'c':
                            x.apply(window, [eval(z.data.substring(2))]);
                            break;
                        case 'x': { var err = eval(z.data.substring(2));
                            // alert( err.javaClassName+": "+err.message );
                            req.errorHandler(err);
                            break;
                        }
                        default: alert("unknown server-response type: "+z.data[0]);
                    };
                };
                req.errorHandler = req.brijj.defaultErrorHandler;
                req.then( zfn, req.brijj.defaultErrorHandler );
                return {
                    except: function(y) {
                        req.errorHandler = y;
                        req.error(y);
                    } 
                }
            }
        }
    },

    // *******************************************************************************
    // only for FormData case
    xhrStateChange: function(req) {
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
                req.handleException(req.errback, { name:"brijj.http." + status, message:req.responseText }); }
            else if (reply == null || reply == "") {
                req.handleException(req.errback, { name:"brijj.missingData", message:"No data received from server" }); }
            else {                     
                var contentType = req.getResponseHeader("Content-Type");
                toEval = reply; }
        }
        catch (ex) { req.handleException(req.errback, ex); }

        req.doResponse(req.callback,req.errback,toEval);
        if (req) delete req;
    },

    handleException: function(errorHandler, reply) {
        errorHandler.apply(window,[reply]);
    },
    doResponse: function(callback, errorHandler, toEval) {
        if (toEval) {
            switch(toEval[0]) {
                case 'c': this.handleCallback(callback, eval(toEval.substring(2))); break;
                case 'x': this.handleException( errorHandler, eval(toEval.substring(2)) ); break;
                default: alert("unknown server-response type: "+toEval[0]);
            }
        }
    },
    handleCallback: function(cb,reply) {
        if (cb == console.log) console.log(reply); else if (cb) cb.apply(window, [ reply ]);
    },

    // *******************************************************************************



    _serialize: function(data) {
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
                                   reply += encodeURIComponent(this._serialize(data[i]));
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
                                   reply += encodeURIComponent(element + ":"+this._serialize(data[element]));
                               }
                               return reply + "}";
                           }
                           break;
            default:
                           alert("serializing unknown data type: "+typeof data);
                           return "u:" + data;
        }
    },

    _sync: function(scriptName, methodName, args) {
        var url = this._path + "/call/" + scriptName + "." +methodName;
        var body = "";
        for (var i=0;i< args.length;i++) body += this._serialize(args[i]) + "\n";

        var req = new XMLHttpRequest();
        req.open("POST", url, false);
        req.send(body);
        var rsp = req.response;
        switch(rsp[0]) {
            case 'c': return eval(rsp.substring(2)); 
            case 'x': throw eval(toEval.substring(2));
            default: alert("unknown server-response type: "+rsp[0]);
        }
    },




    {{functions}}
    }
}]);


/*
   this.defaultErrorHandler = function(ex,q) {
   alert((ex.javaClassName ? ex.javaClassName : "" )+": "+ex.message);
   console.log({error: ex, request: q});
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
// This generates a potential race condition, the send happens before the callback is set
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

*/

