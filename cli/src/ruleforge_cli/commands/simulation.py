from __future__ import annotations

import json
import time

import typer

from ..client import RuleForgeClient, RuleForgeError
from ..output import print_error, print_result

app = typer.Typer(help="Rule simulation — replay historical decisions and compare results")


def _client(url: str | None) -> RuleForgeClient:
    return RuleForgeClient(base_url=url)


@app.command("run")
def run_simulation(
    project: str = typer.Option(..., "--project", "-p", help="Project name"),
    package: str = typer.Option(..., "--package", help="Package ID"),
    files: str = typer.Option(..., "--files", "-f", help="Rule files (semicolon-separated, format: path,version)"),
    start: str = typer.Option(..., "--start", help="Start date (YYYY-MM-DD)"),
    end: str = typer.Option(..., "--end", help="End date (YYYY-MM-DD)"),
    flow_id: str | None = typer.Option(None, "--flow-id", help="Flow ID for flow testing"),
    wait: bool = typer.Option(False, "--wait", "-w", help="Wait for completion"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Start a simulation run (replay historical decisions against new rules)."""
    try:
        client = _client(url)
        data = client.start_simulation(
            project=project,
            package_id=package,
            files=files,
            flow_id=flow_id,
            start_time=start,
            end_time=end,
        )
        if not wait:
            print_result(data, json_output)
            return
        # Poll for completion
        run_id = data.get("runId")
        if not run_id:
            print_error("No runId returned", json_output)
            raise typer.Exit(1)
        while True:
            progress = client.simulation_progress(run_id)
            status = progress.get("status", "UNKNOWN")
            if status in ("COMPLETED", "FAILED", "NOT_FOUND"):
                print_result(progress, json_output)
                break
            if not json_output:
                compared = progress.get("totalCompared", 0)
                total = progress.get("totalLogs", 0)
                typer.echo(f"  {status} ... {compared}/{total}")
            time.sleep(3)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("list")
def list_runs(
    package_path: str = typer.Option(..., "--package-path", "-p", help="Rule package path (project/packageId)"),
    page: int = typer.Option(1, "--page", help="Page number"),
    size: int = typer.Option(20, "--size", help="Page size"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """List simulation run history for a package."""
    try:
        data = _client(url).list_simulation_runs(package_path, page=page, size=size)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("results")
def show_results(
    run_id: int = typer.Option(..., "--run-id", "-r", help="Simulation run ID"),
    page: int = typer.Option(1, "--page", help="Page number"),
    size: int = typer.Option(20, "--size", help="Page size"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Show comparison results for a simulation run."""
    try:
        data = _client(url).simulation_results(run_id, page=page, size=size)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("stats")
def show_stats(
    package_path: str = typer.Option(..., "--package-path", "-p", help="Rule package path"),
    start: str | None = typer.Option(None, "--start", help="Start date filter"),
    end: str | None = typer.Option(None, "--end", help="End date filter"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Show aggregated simulation statistics for a package."""
    try:
        data = _client(url).simulation_stats(package_path, start_time=start, end_time=end)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)
