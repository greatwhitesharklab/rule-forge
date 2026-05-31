from __future__ import annotations

import typer

from . import __version__
from .commands import file, package, project, rule, simulation, test, variable

app = typer.Typer(
    name="rf",
    help="RuleForge CLI — rule engine management and testing",
    no_args_is_help=True,
)

app.add_typer(project.app, name="project")
app.add_typer(file.app, name="file")
app.add_typer(rule.app, name="rule")
app.add_typer(test.app, name="test")
app.add_typer(package.app, name="package")
app.add_typer(simulation.app, name="simulation")
app.add_typer(variable.app, name="variable")


def version_callback(value: bool) -> None:
    if value:
        print(f"rf {__version__}")
        raise typer.Exit()


@app.callback()
def main(
    version: bool = typer.Option(False, "--version", "-v", help="Show version", callback=version_callback, is_eager=True),
) -> None:
    pass
