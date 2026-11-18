package com.torque.voice;

import android.content.Context;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs bundled Apache-2.0 Vosk language and speaker models to private app storage. */
final class VoskModels {
    private static final long MAX_UNCOMPRESSED = 340L * 1024L * 1024L;

    static File[] ensureReady(Context context) throws IOException {
        File base = new File(context.getFilesDir(), "vosk-data");
        File language = new File(base,"vosk-model-small-en-us-0.15");
        File speaker = new File(base,"vosk-model-spk-0.4");
        install(context, base, language, "vosk-english.zip", "conf/model.conf");
        install(context, base, speaker, "vosk-speaker.zip", "mfcc.conf");
        return new File[]{language, speaker};
    }

    private static void install(Context ctx, File base, File modelRoot, String asset, String required) throws IOException {
        File marker=new File(modelRoot, ".torque-ready");
        File must=new File(modelRoot,required);
        if(marker.isFile() && must.isFile())return;
        if(!base.exists()&&!base.mkdirs())throw new IOException("Cannot create local model directory");
        long total=0;
        String prefix=base.getCanonicalPath()+File.separator;
        try(InputStream stream=ctx.getAssets().open(asset);
            ZipInputStream zip=new ZipInputStream(new BufferedInputStream(stream))) {
            ZipEntry entry;
            int count=0;
            while((entry=zip.getNextEntry())!=null) {
                if(++count>10000)throw new IOException("Too many model files");
                String name=entry.getName();
                if(name.startsWith("/") || name.contains("../") || name.contains("\\") ||
                   !name.startsWith(modelRoot.getName()+"/"))
                    throw new IOException("Unsafe model archive entry");
                File target=new File(base,name);
                String resolved=target.getCanonicalPath();
                if(!resolved.startsWith(prefix))throw new IOException("Unsafe model archive path");
                if(entry.isDirectory()){
                    if(!target.isDirectory()&&!target.mkdirs())throw new IOException("Unable to create model directory");
                    continue;
                }
                File parent=target.getParentFile();
                if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Unable to create model folder");
                try(BufferedOutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                    byte[] buf=new byte[32768];int len;
                    while((len=zip.read(buf))!=-1){
                        total+=len;
                        if(total>MAX_UNCOMPRESSED)throw new IOException("Unpacked model exceeds size limit");
                        out.write(buf,0,len);
                    }
                }
                zip.closeEntry();
            }
        }
        if(!must.isFile())throw new IOException("Model files incomplete: "+asset);
        if(!marker.createNewFile() && !marker.exists())throw new IOException("Unable to mark model installation");
    }
}
