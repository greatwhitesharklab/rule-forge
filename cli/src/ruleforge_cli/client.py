from __future__ import annotations

import json
from typing import Any

import httpx

from .config import get_api_key, get_base_url


class RuleForgeError(Exception):
    def __init__(self, message: str, status_code: int | None = None):
        super().__init__(message)
        self.status_code = status_code


class RuleForgeClient:
    def __init__(self, base_url: str | None = None):
        url = (base_url or get_base_url()).rstrip("/")
        self._base = f"{url}/ruleforgeV2"
        self._api_key = get_api_key()

    def _headers(self) -> dict[str, str]:
        h: dict[str, str] = {}
        if self._api_key:
            h["Authorization"] = f"Bearer {self._api_key}"
        return h

    def _post(
        self,
        path: str,
        params: dict[str, Any] | None = None,
        body: Any = None,
    ) -> Any:
        url = f"{self._base}{path}"
        headers = self._headers()
        if body is not None:
            headers["Content-Type"] = "application/json"
            content = json.dumps(body, ensure_ascii=False)
        else:
            content = None
        resp = httpx.post(url, params=params, content=content, headers=headers, timeout=60)
        if resp.status_code != 200:
            raise RuleForgeError(f"HTTP {resp.status_code}: {resp.text}", resp.status_code)
        data = resp.json()
        if isinstance(data, dict) and data.get("status") is False:
            raise RuleForgeError(data.get("msg", "Unknown error"))
        return data

    def _get(self, path: str, params: dict[str, Any] | None = None) -> bytes:
        url = f"{self._base}{path}"
        resp = httpx.get(url, params=params, headers=self._headers(), timeout=60)
        if resp.status_code != 200:
            raise RuleForgeError(f"HTTP {resp.status_code}: {resp.text}", resp.status_code)
        return resp.content

    # ---- Project ----

    def load_projects(self, **kwargs: Any) -> Any:
        params = {k: v for k, v in kwargs.items() if v is not None}
        return self._post("/frame/loadProjects", params=params)

    def create_project(self, name: str, classify: str | None = None) -> Any:
        params: dict[str, Any] = {"newProjectName": name}
        if classify:
            params["classify"] = classify
        return self._post("/frame/createProject", params=params)

    def delete_project(self, path: str) -> Any:
        return self._post("/frame/deleteProject", params={"path": path})

    # ---- File ----

    def create_file(self, path: str, type: str) -> Any:
        return self._post("/frame/createFile", params={"path": path, "type": type})

    def create_folder(self, name: str) -> Any:
        return self._post("/frame/createFolder", params={"fullFolderName": name})

    def delete_file(self, path: str) -> Any:
        return self._post("/frame/deleteFile", params={"path": path})

    def file_rename(self, path: str, new_path: str) -> Any:
        return self._post("/frame/fileRename", params={"path": path, "newPath": new_path})

    def copy_file(self, old_path: str, new_path: str) -> Any:
        return self._post("/frame/copyFile", params={"oldFullPath": old_path, "newFullPath": new_path})

    def file_source(self, path: str, version: str | None = None, env: str | None = None) -> Any:
        params: dict[str, Any] = {"path": path}
        if version:
            params["path"] = f"{path}:{version}"
        if env:
            params["env"] = env
        return self._post("/frame/fileSource", params=params)

    def save_file(self, file: str, content: str, new_version: bool = False) -> Any:
        return self._post("/common/saveFile", params={"file": file, "content": content, "newVersion": new_version})

    def lock_file(self, file: str) -> Any:
        return self._post("/frame/lockFile", params={"file": file})

    def unlock_file(self, file: str) -> Any:
        return self._post("/frame/unlockFile", params={"file": file})

    def file_versions(self, path: str, project: str | None = None, page: int = 1, rows: int = 25) -> Any:
        params: dict[str, Any] = {"path": path, "page": page, "row": rows}
        if project:
            params["project"] = project
        return self._post("/frame/fileVersions", params=params)

    def file_exist_check(self, full_file_name: str) -> Any:
        return self._post("/frame/fileExistCheck", params={"fullFileName": full_file_name})

    # ---- Rule ----

    def load_xml(self, files: str) -> Any:
        return self._post("/common/loadXml", params={"files": files})

    def find_rule_by_key(self, rule_key: str, project_name: str) -> Any:
        return self._post("/common/findRuleByKey", params={"ruleKey": rule_key, "projectName": project_name})

    def load_resource_tree_data(self, project: str | None = None, for_lib: bool | None = None, file_type: str | None = None) -> Any:
        params: dict[str, Any] = {}
        if project:
            params["project"] = project
        if for_lib is not None:
            params["forLib"] = str(for_lib).lower()
        if file_type:
            params["fileType"] = file_type
        return self._post("/common/loadResourceTreeData", params=params)

    # ---- Test ----

    def load_test_variables(self, file_path: str, app_id: str | None = None, project_id: str | None = None) -> Any:
        body: dict[str, Any] = {"filePath": file_path}
        if app_id:
            body["appId"] = app_id
        if project_id:
            body["projectId"] = project_id
        return self._post("/test/variableCategories/load", body=body)

    def fast_test(self, file_path: str, data: list[dict[str, Any]] | None = None, flow_id: str | None = None) -> Any:
        body: dict[str, Any] = {"filePath": file_path}
        if data:
            body["data"] = data
        if flow_id:
            body["flowId"] = flow_id
        return self._post("/test/fast", body=body)

    def load_data_by_app_id(self, app_id: str, project_id: str) -> Any:
        return self._post("/test/data/appId", params={"appId": app_id, "projectId": project_id})

    # ---- Package ----

    def load_packages(self, project: str, env: str | None = None) -> Any:
        params: dict[str, Any] = {"project": project}
        if env:
            params["env"] = env
        return self._post("/packageeditor/loadPackages", params=params)

    def load_package_config(self, project: str) -> Any:
        return self._post("/packageeditor/loadPackageConfig", params={"project": project})

    def load_flows(self) -> Any:
        return self._post("/packageeditor/loadFlows")

    # ---- Variable ----

    def generate_fields(self, clazz: str) -> Any:
        return self._post("/variableeditor/generateFields", params={"clazz": clazz})

    def generate_variable_library(self) -> Any:
        return self._post("/variableeditor/generateVariableLibrary")

    # ---- Simulation ----

    def start_simulation(
        self,
        project: str,
        package_id: str,
        files: str,
        start_time: str,
        end_time: str,
        flow_id: str | None = None,
    ) -> Any:
        body: dict[str, Any] = {
            "project": project,
            "packageId": package_id,
            "files": files,
            "startTime": start_time,
            "endTime": end_time,
        }
        if flow_id:
            body["flowId"] = flow_id
        return self._post("/simulation/startSimulation", body=body)

    def simulation_progress(self, run_id: int) -> Any:
        resp = self._get("/simulation/simulationProgress", params={"runId": run_id})
        return json.loads(resp)

    def simulation_results(self, run_id: int, page: int = 1, size: int = 20) -> Any:
        resp = self._get("/simulation/simulationResults", params={"runId": run_id, "page": page, "size": size})
        return json.loads(resp)

    def list_simulation_runs(self, rule_package_path: str, page: int = 1, size: int = 20) -> Any:
        resp = self._get("/simulation/simulationRuns", params={"rulePackagePath": rule_package_path, "page": page, "size": size})
        return json.loads(resp)

    def simulation_stats(self, rule_package_path: str, start_time: str | None = None, end_time: str | None = None) -> Any:
        params: dict[str, Any] = {"rulePackagePath": rule_package_path}
        if start_time:
            params["startTime"] = start_time
        if end_time:
            params["endTime"] = end_time
        resp = self._get("/simulation/simulationStats", params=params)
        return json.loads(resp)
