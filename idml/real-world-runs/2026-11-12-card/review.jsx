#target indesign
(function(){
var root='/tmp/codex-idml-card';
var old=app.scriptPreferences.userInteractionLevel;var png=app.pngExportPreferences.properties;
function log(path,s){var f=File(path);f.open('a');f.writeln(s);f.close();}
function inspect(d,path){log(path,'version='+app.version+' pages='+d.pages.length);var a=d.allPageItems;for(var i=0;i<a.length;i++){var x=a[i];if(x.constructor.name==='TextFrame'&&x.overflows)log(path,'overflow='+x.id+' label='+x.label);if(x.label.indexOf('rw-')===0)log(path,'target='+x.label+' id='+x.id+' bounds='+x.geometricBounds+(x.constructor.name==='TextFrame'?' story='+x.parentStory.id+' text='+x.parentStory.contents.replace(/\r/g,'|'):''));}for(var i=0;i<d.fonts.length;i++)log(path,'font='+d.fonts[i].name+' status='+d.fonts[i].status);for(var i=0;i<d.links.length;i++)log(path,'link='+d.links[i].name+' status='+d.links[i].status);}
function render(d,file){app.pngExportPreferences.pngExportRange=PNGExportRangeEnum.EXPORT_RANGE;app.pngExportPreferences.pageString='1-2';app.pngExportPreferences.exportingSpread=false;app.pngExportPreferences.exportResolution=180;d.exportFile(ExportFormat.PNG_FORMAT,File(file),false);}
function get(d,key){var a=d.allPageItems;for(var i=0;i<a.length;i++)if(a[i].label===key)return a[i];throw Error('Missing '+key);}
function byid(d,id){var a=d.allPageItems;for(var i=0;i<a.length;i++)if(a[i].id===id)return a[i];throw Error('Missing id '+id);}
function replace(story,a,b){var s=story.contents;var n=s.indexOf(a);if(n<0)throw Error('Text missing '+a);story.characters.itemByRange(n,n+a.length-1).contents=b;}

var names=['01-single-add','02-single-delete','03-single-move','04-both-add-diff-pos','07-same-story-diff-para','09-zorder','10-group-ungroup','11-thread-insert'];
try{
app.scriptPreferences.userInteractionLevel=UserInteractionLevels.NEVER_INTERACT;
var base=app.open(File(root+'/baseline.indd'));inspect(base,root+'/baseline-application.log');render(base,root+'/baseline-page2.png');base.close(SaveOptions.NO);
for(var i=0;i<names.length;i++){
var dir=root+'/'+names[i],path=dir+'/application.log';var d=null;
try{
d=app.open(File(root+'/baseline.indd'));
for(var side=0;side<2;side++){var left=side===0,key=names[i],r1=get(d,'rw-r1'),r2=(key==='02-single-delete'&&side===1)?null:get(d,'rw-r2'),tf1=get(d,'rw-tf1'),tf2=get(d,'rw-tf2'),story=tf1.parentStory;
if(key==='01-single-add'&&left){var n=d.pages[1].rectangles.add({geometricBounds:[44,71,49,77]});n.label='rw-added-left';n.itemLayer=r2.itemLayer;n.sendToBack(r2);}
if(key==='02-single-delete'&&left)r2.remove();
if(key==='03-single-move'&&left)r1.bringToFront();
if(key==='04-both-add-diff-pos'){if(!left){var dummy=d.pages[1].rectangles.add();dummy.remove();}var n=d.pages[1].rectangles.add({geometricBounds:left?[44,71,49,77]:[44,79,49,85]});n.label=left?'rw-added-left':'rw-added-right';n.itemLayer=(left?r1:r2).itemLayer;n.sendToBack(left?r1:r2);}
if(key==='05-move-move-divergent'){if(left)r1.bringToFront();else r1.sendToBack();}
if(key==='06-delete-vs-modify'){if(left)r2.remove();else r2.fillColor=d.swatches.itemByName('Black');}
if(key==='06b-delete-thread-vs-content'){if(left)tf2.remove();else replace(story,'quick','swift');}
if(key==='07-same-story-diff-para'){if(left)replace(story,'quick','swift');else replace(story,'Fourth paragraph baseline','Fourth paragraph revised');}
if(key==='08-same-para-overlap'){if(left)replace(story,'quick','swift');else replace(story,'quick brown','slow red');}
if(key==='08b-same-para-disjoint'){if(left)replace(story,'quick','swift');else replace(story,'lazy','sleepy');}
if(key==='09-zorder'){if(left)r1.sendToBack(r2);else r2.bringToFront(r1);}
if(key==='10-group-ungroup'){if(left)get(d,'rw-g1').ungroup();else get(d,'rw-r3').move(undefined,[2,0]);}
if(key==='10b-divergent-parent'){get(d,'rw-g1').ungroup();if(!left){get(d,'rw-g2').ungroup();d.pages[1].groups.add([get(d,'rw-r3'),get(d,'rw-r6'),get(d,'rw-r7')]).label='rw-new-parent';}}
if(key==='11-thread-insert'&&left){tf1.nextTextFrame=NothingEnum.NOTHING;var n=d.pages[1].textFrames.add({geometricBounds:[44,5,49,30]});n.label='rw-tfX';tf1.nextTextFrame=n;n.nextTextFrame=tf2;}
if(key==='12-next-divergent'){tf1.nextTextFrame=NothingEnum.NOTHING;tf1.nextTextFrame=get(d,left?'rw-tfA':'rw-tfB');}
if(key==='13-parentstory-vs-content'){if(left){tf1.nextTextFrame=NothingEnum.NOTHING;get(d,'rw-story2frame').nextTextFrame=tf2;}else replace(story,'quick','swift');}

}
d.recompose();render(d,dir+'/expected-page2.png');d.exportFile(ExportFormat.INDESIGN_MARKUP,File(dir+'/expected.idml'),false);d.close(SaveOptions.NO);d=null;
d=app.open(File(dir+'/merged.idml'));log(path,'OPEN PASS');inspect(d,path);d.save(File(dir+'/'+names[i]+'-review.indd'));log(path,'SAVE PASS');d.close(SaveOptions.NO);d=null;log(path,'CLOSE PASS');d=app.open(File(dir+'/'+names[i]+'-review.indd'));log(path,'REOPEN PASS');render(d,dir+'/merged-page2.png');d.exportFile(ExportFormat.INDESIGN_MARKUP,File(dir+'/roundtrip.idml'),false);app.activeWindow.activePage=d.pages[1];app.activeWindow.zoom(ZoomOptions.FIT_PAGE);log(path,'RENDER PASS');}
catch(e){log(path,'ERROR '+e+' line '+e.line);if(d)d.close(SaveOptions.NO);}
}
}catch(e){log(root+'/review-errors.log',String(e)+' line '+e.line);}finally{app.scriptPreferences.userInteractionLevel=old;app.pngExportPreferences.properties=png;}
})();
