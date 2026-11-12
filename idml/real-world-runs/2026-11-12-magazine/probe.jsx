#target indesign
(function () {
 var root = '/tmp/codex-idml-real-world';
 var log = File(root + '/probe.txt'); log.open('w');
 var old = app.scriptPreferences.userInteractionLevel;
 try {
 app.scriptPreferences.userInteractionLevel = UserInteractionLevels.NEVER_INTERACT;
 var doc = app.open(File('/tmp/codex-idml-real-world/source.idml'));
 log.writeln('VERSION ' + app.version + ' PAGES ' + doc.pages.length);
 for(var i=0;i<doc.pages.length;i++) {
 var p=doc.pages[i]; log.writeln('PAGE '+p.name+' items '+p.allPageItems.length);
 for(var j=0;j<p.allPageItems.length;j++) {var x=p.allPageItems[j]; log.writeln(x.constructor.name+' id='+x.id+' bounds='+x.geometricBounds + (x.constructor.name=='TextFrame' ? ' story='+x.parentStory.id+' text='+x.contents.substring(0,100).replace(/\r/g,'|') : ''));}
 }
 doc.save(File(root+'/source-normalized.indd'));
 log.writeln('DONE');
 } catch(e){log.writeln('ERROR '+e+' line '+e.line);} finally {log.close();app.scriptPreferences.userInteractionLevel=old;}
})();
