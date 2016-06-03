document.write('hello 4');
if(window.applicationCache) {
  window.applicationCache.onupdateready = function() { 
    location.reload();
  };
}
