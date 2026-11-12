#target indesign
(function(){
var old=app.scriptPreferences.userInteractionLevel;
try{app.scriptPreferences.userInteractionLevel=UserInteractionLevels.NEVER_INTERACT;var d=app.open(File('/tmp/codex-idml-card/baseline.indd'));app.activeWindow.activePage=d.pages[1];app.activeWindow.zoom(ZoomOptions.FIT_PAGE);}finally{app.scriptPreferences.userInteractionLevel=old;}
})();
