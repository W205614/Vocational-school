import hashlib
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


if __name__=='__main__':unittest.main()
