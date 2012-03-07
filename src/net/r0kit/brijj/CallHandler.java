package net.r0kit.brijj;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class CallHandler {

  public void handle(HttpServletRequest request, HttpServletResponse response, boolean iframe) throws ServerException, IOException {
    CallBatch call = new CallBatch(request);
    Object rsp;
    try {
      // Save the batch so marshallException can get at a batch id
      request.setAttribute(ATTRIBUTE_BATCH, call);

      // Various bits of the CallBatch need to be stashed away places
      // storeParsedRequest(request, call);

      call.marshallInbound();
      rsp = call.execute(response);
//       getOutboundMimeType(), outboundPrefix(call.batchId), outboundSuffix(call.batchId));
    } catch (Throwable ex) {
      rsp = ex;
    }
    
    new Outbound(rsp).writeExternal( new JavascriptOutput(response, iframe, call.batchId));
    // call.marshallOutbound(response, iframe);

  }
  
  protected static final String ATTRIBUTE_BATCH = "net.r0kit.brijj.batch";
}
