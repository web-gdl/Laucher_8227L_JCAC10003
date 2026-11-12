#!/usr/bin/env python3
"""Genera el audio del aviso de velocidad (res/raw/exceso_velocidad.ogg).

Voz: Piper «es_MX-claude-high» (español de México, femenina; modelo bajo Apache-2.0, conjunto de datos de HirCoir),
fonemas con espeak-ng. Mediana de F0 medida: ~194 Hz (rango de voz femenina).

    pip install onnxruntime numpy espeakng-loader
    # descargar y descomprimir vits-piper-es_MX-claude-high.tar.bz2 de
    #   https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models   (en esta carpeta)
    python3 generar_aviso.py          # escribe v10.wav
    ffmpeg -i v10.wav -af "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,areverse,\
      silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,areverse,highpass=f=120,\
      acompressor=threshold=-22dB:ratio=3:attack=5:release=80:makeup=4,alimiter=limit=0.94:level=disabled,\
      adelay=250,apad=pad_dur=0.15" -ar 22050 -ac 1 -c:a libvorbis -q:a 5 exceso_velocidad.ogg
Se puede cambiar el texto o la voz y volver a generar el archivo.
"""
import ctypes, json, os, wave, numpy as np, onnxruntime as ort, espeakng_loader, sys
d=os.path.abspath('vits-piper-es_MX-claude-high')
cfg=json.load(open(d+'/es_MX-claude-high.onnx.json'))
lib=ctypes.CDLL(espeakng_loader.get_library_path())
lib.espeak_Initialize.restype=ctypes.c_int
r=lib.espeak_Initialize(2,0,espeakng_loader.get_data_path().encode(),0)  # AUDIO_OUTPUT_SYNCHRONOUS=2? usa 0x...
lib.espeak_SetVoiceByName.argtypes=[ctypes.c_char_p]
print('init',r,'voice',lib.espeak_SetVoiceByName(cfg['espeak']['voice'].encode()))
lib.espeak_TextToPhonemes.restype=ctypes.c_char_p
def phonemize(text):
    buf=ctypes.c_char_p(text.encode()); p=ctypes.pointer(buf); out=[]
    while p[0]:
        s=lib.espeak_TextToPhonemes(ctypes.cast(p,ctypes.POINTER(ctypes.c_void_p)),1,2)
        if not s: break
        out.append(s.decode())
    return ' '.join(out)
text="Está excediendo el límite de velocidad."
ph=phonemize(text); print('fonemas:',ph)
m=cfg['phoneme_id_map']
ids=list(m['^'])
for ch in ph:
    if ch in m: ids+=m[ch]+m['_']
    else: print('sin id',repr(ch))
ids+=m['$']
sess=ort.InferenceSession(d+'/es_MX-claude-high.onnx')
inf=cfg['inference']
def synth(ls):
    y=sess.run(None,{'input':np.array([ids],dtype=np.int64),'input_lengths':np.array([len(ids)],dtype=np.int64),'scales':np.array([inf['noise_scale'],ls,inf['noise_w']],dtype=np.float32)})[0]
    return y.squeeze()
sr=cfg['audio']['sample_rate']
for ls,name in ((1.0,'v10'),(1.12,'v112')):
    s=synth(ls).astype(np.float32); s=s/max(1e-6,np.abs(s).max())*0.95
    w=wave.open(name+'.wav','wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes((s*32767).astype('<i2').tobytes()); w.close()
    f0=[]
    for i in range(0,len(s)-2048,512):
        x=s[i:i+2048]-s[i:i+2048].mean()
        if np.sqrt((x**2).mean())<0.03: continue
        ac=np.correlate(x,x,'full')[2047:]; lo,hi=int(sr/400),int(sr/70); k=lo+np.argmax(ac[lo:hi])
        if ac[k]>0.4*ac[0]: f0.append(sr/k)
    print(name,sr,'Hz',round(len(s)/sr,2),'s F0 mediana',round(float(np.median(f0)),1),'n',len(f0))
