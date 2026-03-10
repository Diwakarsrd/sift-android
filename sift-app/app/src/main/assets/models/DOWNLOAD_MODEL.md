# Download MiniLM Model

Before building, download the embedding model:

```bash
curl -L "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx" \
     -o minilm_int8.onnx
```

Place `minilm_int8.onnx` in this folder:
`app/src/main/assets/models/minilm_int8.onnx`

This file is 22MB. It is required for the app to run.
