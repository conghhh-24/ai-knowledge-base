// Create once per new upload; keep this operation object for every retry.
export function createUploadOperation(file) {
  const key = crypto.randomUUID();
  return Object.freeze({
    key,
    async send(token) {
      const form = new FormData();
      form.append("file", file);
      const response = await fetch("/api/documents/upload", {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Idempotency-Key": key },
        body: form,
      });
      const result = await response.json();
      if (!response.ok || result.code !== 0) {
        const error = new Error(result.message || "上传失败");
        error.code = result.code;
        error.status = response.status;
        throw error;
      }
      return result.data;
    },
  });
}
