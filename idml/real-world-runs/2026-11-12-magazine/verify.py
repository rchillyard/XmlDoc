import json
import sys
from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent

def read(path):
    nodes, labels, parents, orders, texts = {}, {}, {}, {}, {}
    with ZipFile(path) as archive:
        for name in archive.namelist():
            if not name.endswith('.xml'):
                continue
            root = ET.fromstring(archive.read(name))
            for parent in root.iter():
                sid = parent.get('Self')
                if sid and name.startswith(('Spreads/', 'Stories/')):
                    nodes[sid] = parent
                    orders[sid] = [c.get('Self') for c in parent if c.get('Self')]
                    for child in parent:
                        if child.get('Self'):
                            parents[child.get('Self')] = sid
                    label = parent.find('./Properties/Label/KeyValuePair[@Key="Label"]')
                    if label is not None and label.get('Value', '').startswith('rw-'):
                        labels[label.get('Value')[3:]] = sid
                    if parent.tag == 'Story':
                        texts[sid] = ''.join(c.text or '' for c in parent.iter('Content'))
    return dict(nodes=nodes, labels=labels, parents=parents, orders=orders, texts=texts)

def check_case(folder):
    b,l,r = [read(folder / f'{side}.idml') for side in ('base','left','right')]
    checks = {}
    def check(name, condition): checks[name] = bool(condition)
    def sid(v,k): return v['labels'][k]
    def node(v,k): return v['nodes'][sid(v,k)]
    def attr(v,k,a): return node(v,k).get(a)
    def parent(v,k): return v['parents'][sid(v,k)]
    def order(v,k): return v['orders'][parent(v,k)]
    def text(v): return v['texts'][attr(v,'tf1','ParentStory')]
    for side,v in [('left',l),('right',r)]:
        check(side+'_label_identity',all(sid(b,k)==value for k,value in v['labels'].items() if k in b['labels']))
        refs=[]
        for identity,n in v['nodes'].items():
            if n.tag=='TextFrame':
                refs.append(n.get('ParentStory') in v['texts'])
                for a,back in [('NextTextFrame','PreviousTextFrame'),('PreviousTextFrame','NextTextFrame')]:
                    target=n.get(a)
                    if target and target!='n': refs.append(target in v['nodes'] and v['nodes'][target].get(back)==identity)
        check(side+'_story_and_thread_integrity',all(refs))
    key=folder.name.split('-')[0]
    if key in ['01','04']:
        check('left_insertion', 'added-left' in l['labels'])
        if key=='04':
            check('right_insertion','added-right' in r['labels'])
            check('distinct_insert_id',sid(l,'added-left')!=sid(r,'added-right'))
    if key in ['02','06']: check('r2_deleted_left','r2' not in l['labels'])
    if key=='06':check('right_fill_changed',attr(r,'r2','FillColor')!=attr(b,'r2','FillColor'))
    if key=='03':check('order_changed',order(l,'r1')!=order(b,'r1'))
    if key=='05':
        check('left_move',order(l,'r1')!=order(b,'r1'));check('right_move',order(r,'r1')!=order(b,'r1'));check('divergent_order',order(l,'r1')!=order(r,'r1'))
    if key=='06b':check('tf2_deleted','tf2' not in l['labels']);check('story_edited',text(r)!=text(b))
    if key=='07':check('paragraph_edits','swift' in text(l) and 'Fourth paragraph revised' in text(r))
    if key=='08':check('overlap','swift' in text(l) and 'slow red' in text(r))
    if key=='08b':check('disjoint','swift' in text(l) and 'sleepy' in text(r))
    if key=='09':
        check('left_order_changed',order(l,'r1')!=order(b,'r1'));check('right_order_changed',order(r,'r1')!=order(b,'r1'));check('compatible_pair_order',(order(l,'r1').index(sid(l,'r1')) < order(l,'r1').index(sid(l,'r2'))) == (order(r,'r1').index(sid(r,'r1')) < order(r,'r1').index(sid(r,'r2'))))
    if key=='10':
        check('ungrouped',parent(l,'r3')!=parent(b,'r3'));check('retained_group_right',parent(r,'r3')==parent(b,'r3'));check('transform_changed',attr(r,'r3','ItemTransform')!=attr(b,'r3','ItemTransform'))
    if key=='10b':check('two_new_parents',len({parent(b,'r3'),parent(l,'r3'),parent(r,'r3')})==3)
    if key=='11':check('insert_thread',attr(l,'tf1','NextTextFrame')==sid(l,'tfX') and attr(l,'tfX','NextTextFrame')==sid(l,'tf2'))
    if key=='12':check('divergent_thread',attr(l,'tf1','NextTextFrame')==sid(l,'tfA') and attr(r,'tf1','NextTextFrame')==sid(r,'tfB'))
    if key=='13':check('story_reassigned',attr(l,'tf2','ParentStory')!=attr(b,'tf2','ParentStory'));check('old_story_edit',text(r)!=text(b))
    return dict(case=folder.name,covered=all(checks.values()),checks=checks,identities=b['labels'])

if __name__=='__main__':
    results=[]
    for folder in sorted(ROOT.iterdir()):
        if folder.is_dir() and folder.name[:2].isdigit():
            try: result=check_case(folder)
            except Exception as error: result=dict(case=folder.name,covered=False,error=str(error))
            results.append(result)
    (ROOT/'coverage.json').write_text(json.dumps(results,indent=2)+'\n')
    for result in results:print(result['case'],result['covered'],[k for k,v in result.get('checks',{}).items() if not v],result.get('error',''))
