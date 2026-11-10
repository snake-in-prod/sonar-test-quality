// two functions: counted as untested, too small for an issue
interface Api {
  get(): void;
  put(): void;
}

export function a(api: Api) {
  api.get();
}

export function b(api: Api) {
  api.put();
}
