# Mochy Ext Module Store

Edit `store/modules.json` to publish modules to the in-app Module Store without rebuilding the APK.

## Adding a `.zip` module
1. Upload your `.zip` to `store/zips/<id>.zip` (or any public `https://` URL like a GitHub Release).
2. Add an entry to `modules` in `store/modules.json` with:
   - `"install": "zip"`
   - `"zipUrl": "https://raw.githubusercontent.com/Rizky088/Mochy-Music-Custom/main/store/zips/<id>.zip"` (optional if placed at `store/zips/<id>.zip`)
   - `"kind"`: `"source"`, `"look"`, `"player"`, or `"other"`

## Adding a link module
Set `"install": "link"` and `"link": "https://your-module-url"`.
