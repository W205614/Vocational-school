import hashlib
import gzip
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from retire_images import candidates, verify_archive


class ImageRetirementTest(unittest.TestCase):
    def test_stopped_and_other_project_references_are_protected(self):
        images=[{'Id':'final','RepoTags':['tianji-compact/current:local']},
                {'Id':'old','RepoTags':['tianji-opt/old:baseline']},
                {'Id':'shared','RepoTags':['mysql:8.4']},
                {'Id':'unrelated','RepoTags':['another-project:local']},
                {'Id':'dangling','RepoTags':[]}]
        self.assertEqual({'old','dangling'},candidates(images,{'shared','dangling'}, {'final','shared'}))

    def test_foreign_tags_protect_an_owned_or_project_tagged_image(self):
        images=[{'Id':'mixed','RepoTags':['tianji-opt/old:baseline','another-project:local']},
                {'Id':'retagged','RepoTags':['another-project:backup']},
                {'Id':'engine','RepoTags':['docker.elastic.co/elasticsearch/elasticsearch:7.17.29']}]
        self.assertEqual({'engine'},candidates(images,{'mixed','retagged','engine'},set()))

    def test_missing_layers_and_wrong_image_ids_cannot_authorize_removal(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'images.tar';layer=b'layer'
            config=json.dumps({'rootfs':{'type':'layers','diff_ids':['sha256:'+hashlib.sha256(layer).hexdigest()]}}).encode();image='sha256:'+hashlib.sha256(config).hexdigest()
            def write(with_layer,layer_content=layer):
                with tarfile.open(path,'w') as archive:
                    contents={'config.json':config,'manifest.json':json.dumps([{'Config':'config.json','Layers':['layer.tar']}]).encode()}
                    if with_layer:contents['layer.tar']=layer_content
                    for name,data in contents.items():
                        info=tarfile.TarInfo(name);info.size=len(data);archive.addfile(info,io.BytesIO(data))
            write(False)
            with self.assertRaises(KeyError):verify_archive(path,{image})
            write(True)
            with self.assertRaises(RuntimeError):verify_archive(path,{'sha256:wrong'})
            self.assertEqual(64,len(verify_archive(path,{image})))
            write(True,b'corrupt-but-present')
            with self.assertRaises(RuntimeError):verify_archive(path,{image})

    def test_oci_identity_and_compressed_bytes_are_bound_to_the_local_platform(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'oci.tar';contents={};plain=b'local executable layer'
            def blob(data):
                digest='sha256:'+hashlib.sha256(data).hexdigest();name='blobs/sha256/'+digest.split(':')[1]
                contents[name]=data;return {'digest':digest,'size':len(data)}
            def document(value):return blob(json.dumps(value,separators=(',',':')).encode())
            layer=blob(gzip.compress(plain));configuration=document({'architecture':'amd64','os':'linux','rootfs':{'type':'layers','diff_ids':['sha256:'+hashlib.sha256(plain).hexdigest()]}})
            manifest=document({'config':configuration,'layers':[layer]});manifest['platform']={'architecture':'amd64','os':'linux'}
            root=document({'manifests':[manifest,{'digest':'sha256:'+'f'*64,'size':1,'platform':{'architecture':'arm64','os':'linux'}}]})
            contents['index.json']=json.dumps({'manifests':[root]}).encode()
            contents['manifest.json']=json.dumps([{'Config':'blobs/sha256/'+configuration['digest'].split(':')[1],'Layers':['blobs/sha256/'+layer['digest'].split(':')[1]]}]).encode()
            def write():
                with tarfile.open(path,'w') as archive:
                    for name,data in contents.items():
                        info=tarfile.TarInfo(name);info.size=len(data);archive.addfile(info,io.BytesIO(data))
            platforms={root['digest']:{'architecture':'amd64','os':'linux'}}
            write();self.assertEqual(64,len(verify_archive(path,{root['digest']},platforms)))
            with self.assertRaises((RuntimeError,KeyError)):verify_archive(path,{root['digest']},{root['digest']:{'architecture':'arm64','os':'linux'}})
            # Changing the gzip header leaves the uncompressed diff_id unchanged,
            # but the descriptor digest must still reject the altered archive bytes.
            name='blobs/sha256/'+layer['digest'].split(':')[1];original=contents[name]
            altered=bytearray(original);altered[4]^=1;contents[name]=bytes(altered);write()
            with self.assertRaises(RuntimeError):verify_archive(path,{root['digest']},platforms)
            contents[name]=original
            name='blobs/sha256/'+manifest['digest'].split(':')[1];original=contents.pop(name);write()
            with self.assertRaises(KeyError):verify_archive(path,{root['digest']},platforms)
            contents[name]=original
            name='blobs/sha256/'+root['digest'].split(':')[1];contents[name]=b'{}';write()
            with self.assertRaises(RuntimeError):verify_archive(path,{root['digest']},platforms)


if __name__=='__main__':unittest.main()
