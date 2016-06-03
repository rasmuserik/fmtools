document.write('hello 3');
if(window.applicationCache) {
  window.applicationCache.onupdateready = function() { 
    location.reload();
  };
}
