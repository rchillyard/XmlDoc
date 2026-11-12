#target indesign
(function(){
var root='/tmp/codex-idml-real-world';
var old=app.scriptPreferences.userInteractionLevel;var png=app.pngExportPreferences.properties;
function log(path,s){var f=File(path);f.open('a');f.writeln(s);f.close();}
function inspect(d,path){log(path,'version='+app.version+' pages='+d.pages.length);var a=d.allPageItems;for(var i=0;i<a.length;i++){var x=a[i];if(x.constructor.name==='TextFrame'&&x.overflows)log(path,'overflow='+x.id+' label='+x.label);if(x.label.indexOf('rw-')===0)log(path,'target='+x.label+' id='+x.id+' bounds='+x.geometricBounds+(x.constructor.name==='TextFrame'?' story='+x.parentStory.id+' text='+x.parentStory.contents.replace(/\r/g,'|'):''));}for(var i=0;i<d.fonts.length;i++)log(path,'font='+d.fonts[i].name+' status='+d.fonts[i].status);for(var i=0;i<d.links.length;i++)log(path,'link='+d.links[i].name+' status='+d.links[i].status);}
function render(d,file){app.pngExportPreferences.pngExportRange=PNGExportRangeEnum.EXPORT_RANGE;app.pngExportPreferences.pageString='2';app.pngExportPreferences.exportingSpread=false;app.pngExportPreferences.exportResolution=120;d.exportFile(ExportFormat.PNG_FORMAT,File(file),false);}
var names=['01-single-add','02-single-delete','03-single-move','04-both-add-diff-pos','07-same-story-diff-para','10-group-ungroup','11-thread-insert'];
try{
app.scriptPreferences.userInteractionLevel=UserInteractionLevels.NEVER_INTERACT;
var base=app.open(File(root+'/baseline.indd'));inspect(base,root+'/baseline-application.log');render(base,root+'/baseline-page2.png');base.close(SaveOptions.NO);
for(var i=0;i<names.length;i++){
var dir=root+'/'+names[i],path=dir+'/application.log';var d=null;
try{d=app.open(File(dir+'/merged.idml'));log(path,'OPEN PASS');inspect(d,path);d.save(File(dir+'/'+names[i]+'-review.indd'));log(path,'SAVE PASS');d.close(SaveOptions.NO);d=null;log(path,'CLOSE PASS');d=app.open(File(dir+'/'+names[i]+'-review.indd'));log(path,'REOPEN PASS');render(d,dir+'/merged-page2.png');d.exportFile(ExportFormat.INDESIGN_MARKUP,File(dir+'/roundtrip.idml'),false);app.activeWindow.activePage=d.pages[1];app.activeWindow.zoom(ZoomOptions.FIT_PAGE);log(path,'RENDER PASS');}
catch(e){log(path,'ERROR '+e+' line '+e.line);if(d)d.close(SaveOptions.NO);}
}
}catch(e){log(root+'/review-errors.log',String(e)+' line '+e.line);}finally{app.scriptPreferences.userInteractionLevel=old;app.pngExportPreferences.properties=png;}
})();
