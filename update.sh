#!/bin/bash

echo "Este script sobreescribirá cualquier cambio que se haya efectuado en este proyecto. Esta ruta está pensada pra PROducción, por lo que no se esperan cambios que deban ser integrados en control de versiones."

read -p " ¿Estás seguro de continuar? (S/N)" yn
case $yn in
    s|S )
        git restore *
        git pull
        sudo chmod -R +x ./
    ;;
    * )
        echo "Fin del script. No se han realizado cambios."
    ;;
esac
