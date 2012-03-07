
function uploadFiles() {
  var image = document.getElementById('uploadImage');
  var file = document.getElementById('uploadFile');
  var color = document.getElementById('color').value;

  UploadDownload.uploadFiles(image, file, color, function(data) {
    document.getElementById('image').src = data;
  });
}
