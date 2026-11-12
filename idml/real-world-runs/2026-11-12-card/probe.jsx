#target indesign
(function () {
 var root = '/tmp/codex-idml-card';
 var log = File(root + '/probe.txt'); log.open('w');
 var old = app.scriptPreferences.userInteractionLevel;
 try {
 app.scriptPreferences.userInteractionLevel = UserInteractionLevels.NEVER_INTERACT;
 var doc = app.open(File('/tmp/codex-idml-card/source.indd'));
 log.writeln('VERSION ' + app.version + ' PAGES ' + doc.pages.length);
 for(var i=0;i<doc.pages.length;i++) {
 var p=doc.pages[i]; log.writeln('PAGE '+p.name+' items '+p.allPageItems.length);
 for(var j=0;j<p.allPageItems.length;j++) {var x=p.allPageItems[j]; log.writeln(x.constructor.name+' id='+x.id+' bounds='+x.geometricBounds + (x.constructor.name=='TextFrame' ? ' story='+x.parentStory.id+' text='+x.contents.substring(0,100).replace(/\r/g,'|') : ''));}
 }
 for(var f=0;f<doc.fonts.length;f++)log.writeln('FONT '+doc.fonts[f].name+' '+doc.fonts[f].status);for(var k=0;k<doc.links.length;k++)log.writeln('LINK '+doc.links[k].name+' '+doc.links[k].status);
 for(var a=0;a<app.fonts.length;a++){if(app.fonts[a].name.indexOf('Arial')===0)log.writeln('AVAILABLE '+app.fonts[a].name);}
 doc.save(File(root+'/source-normalized.indd'));
 log.writeln('DONE');
 } catch(e){log.writeln('ERROR '+e+' line '+e.line);} finally {log.close();app.scriptPreferences.userInteractionLevel=old;}
})();
