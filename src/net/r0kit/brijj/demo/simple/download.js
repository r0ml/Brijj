
function downloadPdfFile() {
  var pdftext = document.getElementById('pdftext').value;

  UploadDownload.downloadPdfFile(pdftext, function(data) {
    brijj.engine.openInDownload(data);
  });
}
