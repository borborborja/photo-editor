# RAW compatibility

Photo Editor develops RAW files locally with the bundled LibRaw decoder. The extension is only an
import hint: when a provider does not expose a useful filename, or a camera is newer than the app,
the decoder is still attempted after normal bitmap decoding fails. No supported-camera list is
used as a gatekeeper.

Known import hints cover DNG; Canon CR2/CR3/CRW; Nikon NEF/NRW; Sony ARW/SRF/SR2; Fujifilm RAF;
Olympus ORF; Panasonic RW2; Leica RWL; Pentax PEF/PTX; Sigma X3F; Hasselblad 3FR/FFF; Phase One
IIQ; and several additional LibRaw containers. Actual development depends on the LibRaw version,
the camera firmware and the file being intact. A recognised filename makes the editor prefer
sensor-data development over an embedded JPEG preview; it is not a claim that every model in a
family is supported.

For a new or unusual camera, import the original file directly. If it cannot be developed, the
editor may still show an embedded preview where Android can decode one; this fallback does not
claim RAW controls or replace the original in the editable project. Keep a DNG or original+sidecar
archive when maximum long-term portability matters.

The unit tests cover family detection and the importer always keeps an unknown extension eligible
for the decoder fallback. Device/camera regression samples can be added without changing the app's
acceptance policy.
