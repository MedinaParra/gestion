export default {
  serverExternalPackages: ['pdfjs-dist', '@napi-rs/canvas'],
  outputFileTracingIncludes: {
    '/api/sign': ['./node_modules/pdfjs-dist/standard_fonts/**/*', './node_modules/pdfjs-dist/legacy/build/pdf.worker.mjs'],
  },
};
