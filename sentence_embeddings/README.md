# Sentence Embeddings Android Library

This module runs the full flavor's on-device sentence embedding model.

Tokenization is pure Kotlin (`BertWordPieceTokenizer`). It implements the Bert WordPiece pipeline used by `shibing624/text2vec-base-chinese` and is checked against Hugging Face `tokenizers` 0.22.2. The ONNX Runtime package is still a native inference library; the model itself is not reimplemented.

```bash
./gradlew :sentence_embeddings:assembleRelease
```
