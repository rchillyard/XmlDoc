#target indesign
(function(){
var root='/tmp/codex-idml-real-world';
var old=app.scriptPreferences.userInteractionLevel;
function log(s){var f=File(root+'/operations.log');f.open('a');f.writeln(new Date()+' '+s);f.close();}
function get(d,key){var a=d.allPageItems;for(var i=0;i<a.length;i++)if(a[i].label===key)return a[i];throw Error('Missing '+key);}
function byid(d,id){var a=d.allPageItems;for(var i=0;i<a.length;i++)if(a[i].id===id)return a[i];throw Error('Missing id '+id);}
function exportDoc(d,path){d.exportFile(ExportFormat.INDESIGN_MARKUP,File(path),false);}
function record(d,path){var f=File(path);f.open('w');f.writeln('version\t'+app.version);var a=d.allPageItems;for(var i=0;i<a.length;i++){var x=a[i];if(x.label.indexOf('rw-')===0)f.writeln(x.label+'\t'+x.id+'\t'+x.constructor.name+'\t'+x.parent.id+'\t'+x.geometricBounds+(x.constructor.name==='TextFrame'?'\tstory='+x.parentStory.id+'\tprevious='+x.previousTextFrame+'\tnext='+x.nextTextFrame+'\toverflows='+x.overflows:''));}for(var j=0;j<d.fonts.length;j++)f.writeln('font\t'+d.fonts[j].name+'\t'+d.fonts[j].status);for(var k=0;k<d.links.length;k++)f.writeln('link\t'+d.links[k].name+'\t'+d.links[k].status);f.close();}
function replace(story,a,b){var s=story.contents;var n=s.indexOf(a);if(n<0)throw Error('Text missing '+a);story.characters.itemByRange(n,n+a.length-1).contents=b;}
var names=['01-single-add','02-single-delete','03-single-move','04-both-add-diff-pos','05-move-move-divergent','06-delete-vs-modify','06b-delete-thread-vs-content','07-same-story-diff-para','08-same-para-overlap','08b-same-para-disjoint','09-zorder','10-group-ungroup','10b-divergent-parent','11-thread-insert','12-next-divergent','13-parentstory-vs-content'];
var d;
try{
app.scriptPreferences.userInteractionLevel=UserInteractionLevels.NEVER_INTERACT;
d=app.open(File(root+'/source-normalized.indd'));
d.viewPreferences.horizontalMeasurementUnits=MeasurementUnits.MILLIMETERS;d.viewPreferences.verticalMeasurementUnits=MeasurementUnits.MILLIMETERS;
var p=d.pages[1];
byid(d,682).label='rw-r1';byid(d,686).label='rw-r2';byid(d,683).label='rw-r3';byid(d,684).label='rw-r4';
p.groups.add([get(d,'rw-r3'),get(d,'rw-r4')]).label='rw-g1';
byid(d,685).label='rw-r6';var r7=p.rectangles.add({geometricBounds:[150,145,155,152]});r7.label='rw-r7';p.groups.add([get(d,'rw-r6'),r7]).label='rw-g2';
byid(d,2006).label='rw-tf1';byid(d,2028).label='rw-tf2';byid(d,1940).label='rw-tfA';byid(d,1962).label='rw-tfB';byid(d,1984).label='rw-story2frame';
get(d,'rw-tf1').contents='Magazine merge baseline.\rThe quick brown fox jumps over the lazy dog.\rThird paragraph retained.\rFourth paragraph baseline.';
get(d,'rw-tf2').contents='';get(d,'rw-tfA').contents='';get(d,'rw-tfB').contents='';
get(d,'rw-tf1').nextTextFrame=get(d,'rw-tf2');
d.save(File(root+'/baseline.indd'));record(d,root+'/baseline-objects.tsv');exportDoc(d,root+'/base.idml');d.close(SaveOptions.NO);d=null;log('BASELINE saved and closed');
for(var c=0;c<names.length;c++){
var dir=root+'/'+names[c];Folder(dir).create();File(root+'/base.idml').copy(dir+'/base.idml');
for(var side=0;side<2;side++){
try{
d=app.open(File(root+'/baseline.indd'));var left=side===0;var key=names[c];var r1=get(d,'rw-r1'),r2=get(d,'rw-r2');var tf1=get(d,'rw-tf1'),tf2=get(d,'rw-tf2');var story=tf1.parentStory;
log('START '+key+' '+(left?'left':'right'));
if(key==='01-single-add'&&left){var n=d.pages[1].rectangles.add({geometricBounds:[230,140,238,148]});n.label='rw-added-left';n.itemLayer=r2.itemLayer;n.sendToBack(r2);}
if(key==='02-single-delete'&&left)r2.remove();
if(key==='03-single-move'&&left)r1.bringToFront();
if(key==='04-both-add-diff-pos'){if(!left){var dummy=d.pages[1].rectangles.add();dummy.remove();}var n=d.pages[1].rectangles.add({geometricBounds:left?[230,140,238,148]:[230,155,238,163]});n.label=left?'rw-added-left':'rw-added-right';n.itemLayer=(left?r1:r2).itemLayer;n.sendToBack(left?r1:r2);}
if(key==='05-move-move-divergent'){if(left)r1.bringToFront();else r1.sendToBack();}
if(key==='06-delete-vs-modify'){if(left)r2.remove();else r2.fillColor=d.swatches.itemByName('Black');}
if(key==='06b-delete-thread-vs-content'){if(left)tf2.remove();else replace(story,'quick','swift');}
if(key==='07-same-story-diff-para'){if(left)replace(story,'quick','swift');else replace(story,'Fourth paragraph baseline','Fourth paragraph revised');}
if(key==='08-same-para-overlap'){if(left)replace(story,'quick','swift');else replace(story,'quick brown','slow red');}
if(key==='08b-same-para-disjoint'){if(left)replace(story,'quick','swift');else replace(story,'lazy','sleepy');}
if(key==='09-zorder'){if(left)r1.sendToBack(r2);else r2.bringToFront(r1);}
if(key==='10-group-ungroup'){if(left)get(d,'rw-g1').ungroup();else get(d,'rw-r3').move(undefined,[2,0]);}
if(key==='10b-divergent-parent'){get(d,'rw-g1').ungroup();if(!left){get(d,'rw-g2').ungroup();d.pages[1].groups.add([get(d,'rw-r3'),get(d,'rw-r6'),get(d,'rw-r7')]).label='rw-new-parent';}}
if(key==='11-thread-insert'&&left){tf1.nextTextFrame=NothingEnum.NOTHING;var n=d.pages[1].textFrames.add({geometricBounds:[227,36,238,108]});n.label='rw-tfX';tf1.nextTextFrame=n;n.nextTextFrame=tf2;}
if(key==='12-next-divergent'){tf1.nextTextFrame=NothingEnum.NOTHING;tf1.nextTextFrame=get(d,left?'rw-tfA':'rw-tfB');}
if(key==='13-parentstory-vs-content'){if(left){tf1.nextTextFrame=NothingEnum.NOTHING;get(d,'rw-story2frame').nextTextFrame=tf2;}else replace(story,'quick','swift');}
d.recompose();record(d,dir+'/'+(left?'left':'right')+'-objects.tsv');exportDoc(d,dir+'/'+(left?'left':'right')+'.idml');d.close(SaveOptions.NO);d=null;log('OK '+key+' '+(left?'left':'right'));
}catch(e){log('ERROR '+names[c]+' '+side+' '+e+' line '+e.line);if(d){d.close(SaveOptions.NO);d=null;}}
}
}
log('BATCH DONE');
}catch(e){log('FATAL '+e+' line '+e.line);}finally{if(d)d.close(SaveOptions.NO);app.scriptPreferences.userInteractionLevel=old;}
})();
